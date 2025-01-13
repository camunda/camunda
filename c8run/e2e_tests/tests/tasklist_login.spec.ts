import { test, expect } from '@playwright/test';
const playwright = require('playwright');

test('test', async ({ page }) => {
  test.setTimeout(60000);
  await page.goto('http://localhost:8080/tasklist');
  await page.getByLabel('Expand to show filters', { exact: true }).click();
  await page.getByRole('link', { name: 'Assigned to me' }).click();
});
