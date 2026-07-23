import {
  MIGRATION_MATERIALIZED_VIEWS,
  MIGRATION_PRODUCT_AVAILABILITY,
  MIGRATION_PRODUCT_AVAILABILITY_CALCULATE,
  MIGRATION_PRODUCT_AVAILABILITY_COUNT,
  MIGRATION_PRODUCT_AVAILABILITY_REFRESH,
  MIGRATION_PRODUCT_DEMAND_REFRESH,
} from 'api/urls';
import apiClient from 'utils/apiClient';

export default {
  getMaterializedViews: () => apiClient.get(MIGRATION_MATERIALIZED_VIEWS),
  getProductAvailability: () => apiClient.get(MIGRATION_PRODUCT_AVAILABILITY),
  getProductAvailabilityCount: (locationId) => apiClient.get(
    MIGRATION_PRODUCT_AVAILABILITY_COUNT,
    { params: { locationId } },
  ),
  calculateProductAvailability: (locationId) => apiClient.get(
    MIGRATION_PRODUCT_AVAILABILITY_CALCULATE,
    { params: { locationId } },
  ),
  refreshProductAvailability: (locationId) => apiClient.post(
    MIGRATION_PRODUCT_AVAILABILITY_REFRESH,
    null,
    { params: locationId ? { locationId } : {} },
  ),
  refreshProductDemand: () => apiClient.post(MIGRATION_PRODUCT_DEMAND_REFRESH),
};
