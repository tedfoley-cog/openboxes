import {
  SHIPMENT_GOODS_RECEIPT_NOTE_PRINT,
  SHIPMENT_OUTBOUND_RETURN_PRINT,
} from 'api/urls';
import apiClient from 'utils/apiClient';

export default {
  getOutboundReturnPrintData: (id, config) =>
    apiClient.get(SHIPMENT_OUTBOUND_RETURN_PRINT(id), config),
  getGoodsReceiptNotePrintData: (id, config) =>
    apiClient.get(SHIPMENT_GOODS_RECEIPT_NOTE_PRINT(id), config),
};
