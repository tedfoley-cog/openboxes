import {
  LOGIN_API,
  MOBILE_DASHBOARD,
  MOBILE_OUTBOUND_ITEMS,
  MOBILE_PRODUCT_SUMMARIES,
  MOBILE_PRODUCT_SUMMARY_BY_ID,
} from 'api/urls';
import apiClient from 'utils/apiClient';

export default {
  getDashboard: () => apiClient.get(MOBILE_DASHBOARD),
  getProductSummaries: (config) => apiClient.get(MOBILE_PRODUCT_SUMMARIES, config),
  getProductSummary: (id) => apiClient.get(MOBILE_PRODUCT_SUMMARY_BY_ID(id)),
  getOutboundItems: (config) => apiClient.get(MOBILE_OUTBOUND_ITEMS, config),
  login: (payload) => apiClient.post(LOGIN_API, payload),
};
