import { test, expect } from '@playwright/test';
const playwright = require('playwright');

test('test', async ({ page }) => {
  test.setTimeout(60000);
  await page.goto('http://localhost:8088/operate');
  await page.getByPlaceholder('Username').click();
  await page.getByPlaceholder('Username').fill('demo');
  await page.getByPlaceholder('Username').press('Tab');
  await page.getByPlaceholder('Password').fill('demo');
  await page.getByRole('button', { name: 'Login' }).click();
  await page.getByRole('link', { name: 'Processes' }).click();
});
