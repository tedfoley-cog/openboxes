import { useState } from 'react';

import _ from 'lodash';
import queryString from 'query-string';
import { useSelector } from 'react-redux';

import locationApi from 'api/services/LocationApi';
import filterFields from 'components/location/FilterFields';
import useCommonFilters from 'hooks/list-pages/useCommonFilters';
import useCommonFiltersCleaner from 'hooks/list-pages/useCommonFiltersCleaner';
import { clearQueryParams, transformFilterParams } from 'utils/list-utils';
import { fetchLocationGroupById, fetchOrganization } from 'utils/option-utils';
import splitTranslation from 'utils/translation-utils';

const DEPOT = 'DEPOT';

const useLocationFilters = () => {
  const {
    filterParams,
    setFilterParams,
    defaultFilterValues,
    setDefaultFilterValues,
    filtersInitialized,
    setFiltersInitialized,
    history,
  } = useCommonFilters();

  const [locationTypes, setLocationTypes] = useState([]);

  const { locale } = useSelector((state) => ({
    locale: state.session.activeLanguage,
  }));

  const fetchLocationTypes = async () => {
    const response = await locationApi.getLocationTypes();
    const types = _.map(response.data.data, (locationType) => ({
      ...locationType,
      value: locationType.id,
      label: splitTranslation(locationType.name, locale),
    }));
    setLocationTypes(types);
    return types;
  };

  const clearFilterValues = () => {
    const { pathname, search } = history.location;
    const queryParams = queryString.parse(search);
    const clearedParams = clearQueryParams({ fieldsToIgnore: [], queryParams });
    history.push({ pathname, search: clearedParams });
  };

  const initializeDefaultFilterValues = async () => {
    const defaultValues = Object.keys(filterFields)
      .reduce((acc, key) => ({ ...acc, [key]: '' }), {});

    const queryProps = queryString.parse(history.location.search);
    const types = await fetchLocationTypes();

    if (queryProps.searchTerm) {
      defaultValues.searchTerm = queryProps.searchTerm;
    }
    if (queryProps.locationType) {
      defaultValues.locationType = types
        .find(({ id }) => id === queryProps.locationType);
    } else {
      // The legacy location list defaults to filtering by the Depot location type
      defaultValues.locationType = types
        .find(({ locationTypeCode }) => locationTypeCode === DEPOT);
    }
    if (queryProps.locationGroup) {
      defaultValues.locationGroup = await fetchLocationGroupById(queryProps.locationGroup);
    }
    if (queryProps.organization) {
      const organization = await fetchOrganization(queryProps.organization);
      if (organization) {
        organization.label = organization.displayName ?? organization.name;
        defaultValues.organization = organization;
      }
    }

    setDefaultFilterValues(defaultValues);
    setFiltersInitialized(true);
  };

  const setFilterValues = (values) => {
    const filterAccessors = {
      searchTerm: { name: 'searchTerm' },
      locationType: { name: 'locationType', accessor: 'id' },
      locationGroup: { name: 'locationGroup', accessor: 'id' },
      organization: { name: 'organization', accessor: 'id' },
    };
    const transformedParams = transformFilterParams(values, filterAccessors);
    const queryFilterParams = queryString.stringify(transformedParams);
    const { pathname } = history.location;
    if (Object.keys(values).length) {
      history.push({ pathname, search: queryFilterParams });
    }
    setFilterParams(values);
  };

  useCommonFiltersCleaner({
    filtersInitialized,
    initializeDefaultFilterValues,
    clearFilterValues,
  });

  return {
    defaultFilterValues,
    setFilterValues,
    filterParams,
    locationTypes,
  };
};

export default useLocationFilters;
