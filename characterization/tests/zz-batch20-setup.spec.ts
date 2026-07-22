import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import { ADMIN, LOCATIONS, PRODUCTS, url } from '../fixtures/constants';
import { newApiSession, findProductId, findSupplierLocationId, currentUserId } from '../fixtures/api';

// TEMP setup for batch20 manual testing: creates a PLACED purchase order with
// two line items (input to the receive-order workflow).
test('setup: placed purchase order with two items', async () => {
  const ctx = await newApiSession(ADMIN.username, ADMIN.password);
  const supplierId = await findSupplierLocationId(ctx, LOCATIONS.mainSupplier.name);
  const productNames = Object.values(PRODUCTS).slice(0, 2).map((p: any) => p.name);
  const orderedById = await currentUserId(ctx);
  const now = new Date();
  const dateOrdered = `${now.getMonth() + 1}/${now.getDate()}/${now.getFullYear()}`;

  const headerRes = await ctx.post(url('/purchaseOrder/saveOrderDetails'), {
    form: {
      'order.id': '',
      'orderType.id': 'PURCHASE_ORDER',
      name: 'Batch20 manual receive test PO',
      'origin.id': supplierId,
      'destination.id': LOCATIONS.mainWarehouse.id,
      'destinationParty.id': LOCATIONS.mainWarehouse.organizationId,
      'orderedBy.id': orderedById,
      dateOrdered,
      currencyCode: 'USD',
    },
    maxRedirects: 0,
  });
  expect(headerRes.status()).toBe(302);
  const orderId = headerRes.headers()['location'].match(/addItems\/([0-9a-f]+)/)![1];

  const quantities = [10, 4];
  for (let i = 0; i < 2; i += 1) {
    const productId = await findProductId(ctx, productNames[i]);
    const itemRes = await ctx.post(url('/order/saveOrderItem'), {
      form: {
        'order.id': orderId,
        'orderItem.id': '',
        'product.id': productId,
        quantity: String(quantities[i]),
        quantityPerUom: '1',
        unitPrice: '2.50',
      },
    });
    expect(itemRes.status()).toBe(200);
  }

  const placeRes = await ctx.get(url(`/order/placeOrder/${orderId}`), { maxRedirects: 0 });
  expect(placeRes.status()).toBe(302);

  fs.writeFileSync('/tmp/batch20-ids.json', JSON.stringify({ orderId, productNames, quantities }, null, 2));
  console.log('ORDER_ID=' + orderId);
});
