import { test, expect } from '@playwright/test';

test('logs in and filters tasks assigned to the current user', async ({ page }) => {
  test.setTimeout(60000);
  await page.goto('http://localhost:8080/tasklist');
  await page.getByPlaceholder('Username').click();
  await page.getByPlaceholder('Username').fill('demo');
  await page.getByPlaceholder('Username').press('Tab');
  await page.getByPlaceholder('Password').fill('demo');
  await page.getByRole('button', { name: 'Login' }).click();
  await page.getByRole('button', { name: 'Filters', exact: true }).click();
  await page.getByRole('menuitem', { name: 'Assigned to me', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Filters', exact: true })).toHaveText('Assigned to me');
});
