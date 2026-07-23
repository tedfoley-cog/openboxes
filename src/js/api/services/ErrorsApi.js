import { ERROR_DETAILS_API } from 'api/urls';
import apiClient from 'utils/apiClient';

export default {
  getErrorDetails: () => apiClient.get(ERROR_DETAILS_API),
};
