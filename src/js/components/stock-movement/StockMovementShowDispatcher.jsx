import React, { useEffect, useState } from 'react';

import { useParams } from 'react-router-dom';
import Alert from 'react-s-alert';

import stockMovementApi from 'api/services/StockMovementApi';
import ReturnsShow from 'components/returns/ReturnsShow';
import StockMovementShow from 'components/stock-movement/StockMovementShow';

// The legacy StockMovementController.show action rendered /returns/show.gsp
// for order-based stock movements (returns) and show.gsp for requisition-based
// ones. Both now share the /stockMovement/show/:id route, so dispatch on the
// movement type.
const StockMovementShowDispatcher = () => {
  const { stockMovementId } = useParams();
  const [isOrderBased, setIsOrderBased] = useState(null);

  useEffect(() => {
    setIsOrderBased(null);
    stockMovementApi.getDetails(stockMovementId)
      .then(({ data }) => {
        // same check as the legacy controller: stockMovement?.order
        setIsOrderBased(!!data?.data?.order);
      })
      .catch((err) => {
        const message = err?.response?.data?.errorMessage;
        if (message) {
          Alert.error(message);
        }
      });
  }, [stockMovementId]);

  if (isOrderBased === null) {
    return null;
  }
  return isOrderBased ? <ReturnsShow /> : <StockMovementShow />;
};

export default StockMovementShowDispatcher;
