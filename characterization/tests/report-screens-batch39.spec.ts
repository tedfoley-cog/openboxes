import { test, expect, Page } from '@playwright/test';
import { login } from '../fixtures/auth';
import { LOCATIONS, url } from '../fixtures/constants';
import { captureStep, resetStepCounter } from '../fixtures/screenshots';

/**
 * Batch 39: migrated report/showTransactionReport to React.
 *
 * Asserts the React screen renders the same data as the REST endpoints it
 * consumes (/api/reports/transaction-report and
 * /api/reports/transaction-report-metadata).
 */

// The pinned released image (characterization job) predates the Batch 39
// endpoints/screen; these tests run against source builds (characterization-java21).
async function skipUnlessBatch39(page: Page): Promise<void> {
  const res = await page.request.get(url('/api/reports/transaction-report-metadata'));
  test.skip(res.status() !== 200, 'Batch 39 endpoints not present in target build (pinned released image)');
}

test('report/showTransactionReport renders the React transaction report', async ({ page }) => {
  resetStepCounter();
  const FLOW = 'transaction-report-react';
  await login(page);
  await skipUnlessBatch39(page);

  const locationsRes = await page.request.get(url('/api/locations'));
  const mainId = (await locationsRes.json()).data
    .find((loc) => loc.name === LOCATIONS.mainWarehouse.name).id;

  const metadataRes = await page.request.get(
    url(`/api/reports/transaction-report-metadata?locationId=${mainId}`),
  );
  const metadata = (await metadataRes.json()).data;

  const apiRes = await page.request.get(
    url(`/api/reports/transaction-report?locationId=${mainId}&startDate=01/01/2000&endDate=01/01/2020`),
  );
  const apiRows = (await apiRes.json()).data;

  await page.goto(url('/report/showTransactionReport'));
  // The location defaults to the current location once options load
  await expect(page.locator('[data-testid="location-select"]')).toContainText(LOCATIONS.mainWarehouse.name);
  await expect(page.locator('[data-testid="metadata-product-count"]'))
    .toHaveText(Number(metadata.productCount).toLocaleString('en-US'));
  await expect(page.locator('[data-testid="metadata-transaction-count"]'))
    .toHaveText(Number(metadata.transactionCount).toLocaleString('en-US'));

  await page.fill('#start-date-input', '2000-01-01');
  await page.fill('#end-date-input', '2020-01-01');
  await page.click('[data-testid="run-report-button"]');
  await page.waitForSelector('[data-testid="transaction-report-table"]');
  await captureStep(page, FLOW, 'results');

  const rows = page.locator('[data-testid="transaction-report-table"] tbody tr');
  await expect(rows).toHaveCount(Math.max(apiRows.length, 1)); // empty table renders one placeholder row
  if (apiRows.length) {
    await expect(rows.first().locator('td').first()).toHaveText(apiRows[0].productCode ?? '');

    // Row click opens the per-product transaction details modal
    await rows.first().click();
    await page.waitForSelector('[data-testid="transaction-report-details-table"]');
    await captureStep(page, FLOW, 'details-modal');
    const detailRows = page.locator('[data-testid="transaction-report-details-table"] tbody tr');
    expect(await detailRows.count()).toBeGreaterThanOrEqual(2); // opening + closing balance rows
  }
});
