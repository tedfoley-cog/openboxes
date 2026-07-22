import {
  ADMIN_STATUS_API,
  ADMIN_UPGRADE_API,
  ADMIN_UPGRADE_DEPLOY_API,
  ADMIN_UPGRADE_DOWNLOAD_API,
} from 'api/urls';
import apiClient from 'utils/apiClient';

export default {
  getStatus: () => apiClient.get(ADMIN_STATUS_API),
  getUpgrade: () => apiClient.get(ADMIN_UPGRADE_API),
  downloadUpgrade: (payload) => apiClient.post(ADMIN_UPGRADE_DOWNLOAD_API, payload),
  deployUpgrade: (payload) => apiClient.post(ADMIN_UPGRADE_DEPLOY_API, payload),
};
