/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 **/
package org.pih.warehouse.data

import java.util.regex.Pattern

/**
 * Validates queries that are stored as data (e.g. custom data exports) before they are
 * handed to the database.
 *
 * A query is only accepted when it is a single statement that starts with SELECT (or a
 * WITH clause), contains no comment markers (which can be used to smuggle a payload past
 * the keyword checks) and contains no statement or clause that can write to the database,
 * read the file system or assign a variable.
 *
 * The keyword check is applied to the whole statement, so a query with a string literal
 * such as 'do not delete' is rejected as well. That is intentional: a stored query that
 * cannot be proven read-only is not executed.
 */
class DataExportQueryValidator {

    private static final Pattern READ_ONLY_STATEMENT = ~/(?is)^(select|with)\b.*/

    private static final Pattern COMMENT_MARKER = ~/(--|#|\/\*)/

    private static final Pattern FORBIDDEN_CONSTRUCT = ~/(?is)\b(alter|analyze|attach|begin|call|commit|copy|create|delete|do|drop|execute|grant|handler|insert|into|load|load_file|lock|merge|prepare|rename|replace|revoke|rollback|savepoint|set|shutdown|start|truncate|update|upsert|use|xa)\b|:=/

    static boolean isReadOnlyQuery(String query) {
        String statement = normalize(query)
        if (!statement) {
            return false
        }
        // A remaining semicolon means the query holds more than one statement
        if (statement.contains(";")) {
            return false
        }
        if (COMMENT_MARKER.matcher(statement).find()) {
            return false
        }
        if (!READ_ONLY_STATEMENT.matcher(statement).matches()) {
            return false
        }
        return !FORBIDDEN_CONSTRUCT.matcher(statement).find()
    }

    private static String normalize(String query) {
        String statement = query?.trim()
        while (statement?.endsWith(";")) {
            statement = statement.substring(0, statement.length() - 1).trim()
        }
        return statement ?: null
    }
}
