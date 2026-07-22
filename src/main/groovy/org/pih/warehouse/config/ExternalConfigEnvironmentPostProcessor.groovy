/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 **/
package org.pih.warehouse.config

import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path

import grails.util.Environment
import org.grails.config.NavigableMapPropertySource
import org.grails.config.PropertySourcesConfig
import org.grails.config.yaml.YamlPropertySourceLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader

/**
 * Loads the config files listed in `grails.config.locations`.
 *
 * The external-config plugin (org.grails.plugins:external-config:2.0.0)
 * implemented Spring Boot 2's SpringApplicationRunListener contract, which is
 * incompatible with Spring Boot 3, and the plugin has no Grails 7 release.
 * This EnvironmentPostProcessor reproduces the plugin's behavior 1:1:
 *
 *  - locations are read from `grails.config.locations` (a per-environment
 *    `environments.<env>.grails.config.locations` list takes precedence);
 *  - `~/` prefixes expand to the user's home directory; placeholders such as
 *    `${catalina.base}` resolve against system properties/the environment;
 *  - `file:` locations whose filename contains `*` expand as wildcards;
 *  - `.groovy` files parse with ConfigSlurper (with access to the current
 *    config as its binding), `.yml` files with Grails' YAML loader, anything
 *    else as Java properties;
 *  - missing files are skipped;
 *  - each file becomes a property source added with addFirst(), so later
 *    entries in the list override earlier ones, and all of them override
 *    application.yml.
 */
class ExternalConfigEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger('grails.plugin.externalconfig.ExternalConfig')

    private final ResourceLoader resourceLoader = new DefaultResourceLoader()
    private final YamlPropertySourceLoader yamlPropertySourceLoader = new YamlPropertySourceLoader()
    private final String userHome = System.properties.getProperty('user.home')
    private final String separator = System.properties.getProperty('file.separator')

    @Override
    void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List locations = getLocations(environment)
        String encoding = environment.getProperty('grails.config.encoding', String, 'UTF-8')

        for (location in locations) {
            MapPropertySource propertySource = null
            Map currentProperties = new PropertySourcesConfig(environment.propertySources)
            String finalLocation = environment.resolvePlaceholders(location as String)
            Resource resource = resourceLoader.getResource(finalLocation)
            if (resource.exists()) {
                if (finalLocation.endsWith('.groovy')) {
                    propertySource = loadGroovyConfig(resource, encoding, currentProperties)
                }
                else if (finalLocation.endsWith('.yml')) {
                    propertySource = loadYamlConfig(resource)
                }
                else {
                    propertySource = loadPropertiesConfig(resource)
                }
            }
            else {
                log.debug('Config file {} not found', finalLocation)
            }
            if (propertySource?.getSource() && !propertySource.getSource().isEmpty()) {
                environment.propertySources.addFirst(propertySource)
            }
        }
    }

    private List getLocations(ConfigurableEnvironment environment) {
        List locations = environment.getProperty('grails.config.locations', List, [])
        String environmentSpecificKey = "environments.${Environment.current.name}.grails.config.locations"
        locations = environment.getProperty(environmentSpecificKey, List, locations)
        return locations.collectMany { location ->
            if (location instanceof CharSequence) {
                location = replaceUserHomePrefix(location as String)
                List expandedLocations = handleWildcardLocation(location as String)
                if (expandedLocations) {
                    return expandedLocations
                }
            }
            return [location]
        }
    }

    private List handleWildcardLocation(String location) {
        if (location.startsWith('file:')) {
            String locationFileName = location.tokenize(separator)[-1]
            if (locationFileName.contains('*')) {
                String parentLocation = location - locationFileName
                try {
                    Resource resource = resourceLoader.getResource(parentLocation)
                    if (resource.file.exists() && resource.file.isDirectory()) {
                        Path dir = resource.file.toPath()
                        DirectoryStream<Path> stream = Files.newDirectoryStream(dir, locationFileName)
                        return stream.collect { Path p -> "file:${p.toAbsolutePath()}" as String }
                    }
                }
                catch (FileNotFoundException ignore) {
                    return null
                }
            }
        }
        return null
    }

    private String replaceUserHomePrefix(String location) {
        if (userHome && location.startsWith('~/')) {
            location = "file:${userHome}${location[1..-1]}"
        }
        return location
    }

    private MapPropertySource loadGroovyConfig(Resource resource, String encoding, Map currentConfig) {
        log.info('Loading groovy config file {}', resource.URI)
        String configText = resource.inputStream.getText(encoding)
        ConfigSlurper slurper = new ConfigSlurper(Environment.current.name)
        WriteFilteringMap filterMap = new WriteFilteringMap(currentConfig)
        slurper.binding = filterMap
        Map properties = configText ? slurper.parse(configText)?.flatten() : [:]
        properties.putAll(filterMap.writtenValues)
        return new MapPropertySource(resource.filename, properties)
    }

    private NavigableMapPropertySource loadYamlConfig(Resource resource) {
        log.info('Loading YAML config file {}', resource.URI)
        return yamlPropertySourceLoader.load(resource.filename, resource, null)?.first() as NavigableMapPropertySource
    }

    private MapPropertySource loadPropertiesConfig(Resource resource) {
        log.info('Loading properties config file {}', resource.URI)
        Properties properties = new Properties()
        properties.load(resource.inputStream)
        return new MapPropertySource(resource.filename, properties as Map)
    }

    /**
     * A map wrapper that records writes without letting them touch the
     * underlying map, so top-level assignments in a .groovy config script
     * are captured and merged into the resulting property source (matching
     * the external-config plugin's WriteFilteringMap).
     */
    private static class WriteFilteringMap implements Map<String, Object> {

        @Delegate
        private final Map<String, Object> delegate

        final Map<String, Object> writtenValues = [:]

        WriteFilteringMap(Map<String, Object> delegate) {
            this.delegate = delegate
        }

        @Override
        Object put(String key, Object value) {
            writtenValues.put(key, value)
            return delegate.get(key)
        }

        @Override
        void putAll(Map<? extends String, ?> m) {
            writtenValues.putAll(m)
        }

        @Override
        Object get(Object key) {
            return writtenValues.containsKey(key) ? writtenValues.get(key) : delegate.get(key)
        }
    }
}
