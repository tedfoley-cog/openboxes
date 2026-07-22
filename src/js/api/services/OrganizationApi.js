import {
  ORGANIZATION_API,
  ORGANIZATION_BY_ID,
  ORGANIZATION_DETAILS,
  ORGANIZATION_ROLE_TYPE_OPTIONS,
  PARTY_TYPE_OPTIONS,
} from 'api/urls';
import apiClient from 'utils/apiClient';

export default {
  getOrganizationDetails: (id) => apiClient.get(ORGANIZATION_DETAILS(id)),
  createOrganization: (payload) => apiClient.post(ORGANIZATION_API, payload),
  updateOrganization: (id, payload) => apiClient.put(ORGANIZATION_BY_ID(id), payload),
  deleteOrganization: (id) => apiClient.delete(ORGANIZATION_BY_ID(id)),
  getPartyTypeOptions: () => apiClient.get(PARTY_TYPE_OPTIONS),
  getOrganizationRoleTypeOptions: () => apiClient.get(ORGANIZATION_ROLE_TYPE_OPTIONS),
};
