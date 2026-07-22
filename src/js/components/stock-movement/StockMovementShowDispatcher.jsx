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
  const [isFromOrder, setIsFromOrder] = useState(null);

  useEffect(() => {
    setIsFromOrder(null);
    stockMovementApi.getDetails(stockMovementId)
      .then(({ data }) => {
        setIsFromOrder(!!data?.data?.isFromOrder);
      })
      .catch((err) => {
        const message = err?.response?.data?.errorMessage;
        if (message) {
          Alert.error(message);
        }
      });
  }, [stockMovementId]);

  if (isFromOrder === null) {
    return null;
  }
  return isFromOrder ? <ReturnsShow /> : <StockMovementShow />;
};

export default StockMovementShowDispatcher;
