import { test, expect, Page } from '@playwright/test';
import { url } from '../fixtures/constants';
import { login } from '../fixtures/auth';
import { captureStep, resetStepCounter } from '../fixtures/screenshots';

/**
 * React screens for the outbound return delivery note print, goods receipt
 * note print and the receive order workflow (Phase 2, Batch 20).
 *
 * The legacy /deliveryNote/printOutboundReturn, /goodsReceiptNote/print and
 * /receiveOrderWorkflow/receiveOrder URLs now render the React SPA backed by
 * the new shipment print and receive order JSON APIs.
 */

async function firstShipmentId(page: Page): Promise<string | null> {
  const shipments = (await (await page.request.get(url('/api/generic/shipment/?max=1'))).json())
    .data as Array<{ id: string }>;
  return shipments.length ? shipments[0].id : null;
}

async function firstOrderId(page: Page): Promise<string | null> {
  const orders = (await (await page.request.get(url('/api/generic/order/?max=1'))).json())
    .data as Array<{ id: string }>;
  return orders.length ? orders[0].id : null;
}

test.describe('batch 20 shipments & receiving react screens', () => {
  test.beforeEach(async ({ page }) => {
    resetStepCounter();
    await login(page);
  });

  test('outbound return delivery note print renders the shipment from the API', async ({ page }) => {
    const shipmentId = await firstShipmentId(page);
    test.skip(!shipmentId, 'seeded dataset has no shipments');

    const probe = await page.request.get(url(`/api/shipments/${shipmentId}/outboundReturnPrint`));
    test.skip(probe.status() !== 200, 'app build does not expose the Batch 20 endpoints (pinned released image)');
    const printData = (await probe.json()).data;

    await page.goto(url(`/deliveryNote/printOutboundReturn/${shipmentId}`));
    await expect(page.getByTestId('outbound-return-print-page')).toBeVisible();
    await expect(page.getByTestId('outbound-return-print-shipment-number'))
      .toContainText(printData.shipmentNumber);
    if (printData.origin?.name) {
      await expect(page.getByTestId('outbound-return-print-origin')).toHaveText(printData.origin.name);
    }
    if (printData.destination?.name) {
      await expect(page.getByTestId('outbound-return-print-destination')).toHaveText(printData.destination.name);
    }
    // One row per shipment item, plus one row per receipt item when the
    // received product/lot/expiry differs from the shipped item (the legacy
    // table's strikethrough behavior).
    const expectedRows = printData.shipmentItems.reduce((sum: number, item: any) => {
      const changed = item.receiptItems.some((ri: any) => ri.productId !== item.productId
        || (ri.lotNumber ?? '') !== (item.lotNumber ?? '')
        || (ri.expirationDate ?? null) !== (item.expirationDate ?? null));
      return sum + 1 + (changed ? item.receiptItems.length : 0);
    }, 0);
    await expect(page.locator('[data-testid="outbound-return-print-items-table"] tbody tr'))
      .toHaveCount(expectedRows);
    await captureStep(page, 'outbound-return-print', 'react');
  });

  test('goods receipt note print renders the shipment receipts from the API', async ({ page }) => {
    const shipmentId = await firstShipmentId(page);
    test.skip(!shipmentId, 'seeded dataset has no shipments');

    const probe = await page.request.get(url(`/api/shipments/${shipmentId}/goodsReceiptNotePrint`));
    test.skip(probe.status() !== 200, 'app build does not expose the Batch 20 endpoints (pinned released image)');
    const printData = (await probe.json()).data;

    await page.goto(url(`/goodsReceiptNote/print/${shipmentId}`));
    await expect(page.getByTestId('grn-print-page')).toBeVisible();
    await expect(page.getByTestId('grn-print-shipment-number'))
      .toContainText(printData.shipmentNumber);
    if (printData.status) {
      await expect(page.getByTestId('grn-print-status')).toHaveText(printData.status);
    }
    // One row per receipt item, plus a struck-through original row for each
    // shipment item that was split during receiving.
    const expectedRows = printData.shipmentItems.reduce(
      (sum: number, item: any) => sum + item.receiptItems.length + (item.hasSplit ? 1 : 0),
      0,
    );
    await expect(page.locator('[data-testid="grn-print-items-table"] tbody tr'))
      .toHaveCount(expectedRows);
    await captureStep(page, 'grn-print', 'react');
  });

  test('receive order workflow walks shipment details, items and confirmation', async ({ page }) => {
    const orderId = await firstOrderId(page);
    test.skip(!orderId, 'seeded dataset has no orders');

    const probe = await page.request.get(url(`/api/orders/${orderId}/receiveOrder`));
    test.skip(probe.status() !== 200, 'app build does not expose the Batch 20 endpoints (pinned released image)');
    const orderData = (await probe.json()).data;

    await page.goto(url(`/receiveOrderWorkflow/receiveOrder/${orderId}`));
    await expect(page.getByTestId('receive-order-page')).toBeVisible();
    await expect(page.getByTestId('receive-order-summary-order-number'))
      .toContainText(orderData.orderNumber);
    await expect(page.getByTestId('receive-order-shipment-details')).toBeVisible();
    await captureStep(page, 'receive-order', 'react-shipment-details');

    // Next without required fields surfaces the legacy validation errors
    await page.getByTestId('receive-order-next-button').click();
    await expect(page.getByTestId('receive-order-errors')).toBeVisible();
    await captureStep(page, 'receive-order', 'react-validation-errors');

    // Fill in the shipment details
    await page.getByTestId('receive-order-shipment-type').locator('input').first().focus();
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');
    const today = new Date();
    const dateText = `${today.toLocaleString('en-US', { month: 'short' })} ${String(today.getDate()).padStart(2, '0')}, ${today.getFullYear()}`;
    await page.getByTestId('receive-order-shipped-on').locator('input').fill(dateText);
    await page.keyboard.press('Escape');
    await page.getByTestId('receive-order-delivered-on').locator('input').fill(dateText);
    await page.keyboard.press('Escape');
    await page.getByTestId('receive-order-recipient').locator('input').first().fill('admin');
    await page.waitForTimeout(1500);
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');

    await page.getByTestId('receive-order-next-button').click();
    await expect(page.getByTestId('receive-order-process-items')).toBeVisible();
    await captureStep(page, 'receive-order', 'react-process-items');

    // Same order item rows as the API returns
    if (orderData.orderItems.length) {
      await expect(page.locator('[data-testid="receive-order-item-row"]'))
        .toHaveCount(orderData.orderItems.length);
    }

    await page.getByTestId('receive-order-next-button').click();
    await expect(page.getByTestId('receive-order-confirm')).toBeVisible();
    await captureStep(page, 'receive-order', 'react-confirm');
  });
});
