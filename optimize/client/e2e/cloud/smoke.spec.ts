/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Runs against Optimize in SaaS mode with a shared Auth0 user, so it uses neither seeded data nor
// the self-managed fixtures.
import {expect, test} from '@playwright/test';

import {HomePage} from '../pages/HomePage';
import {ReportPage} from '../pages/ReportPage';
import {TemplateDialog} from '../pages/components/TemplateDialog';
import {LOGIN_TIMEOUT} from '../setup/login';

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing environment variable ${name} for the cloud smoke test`);
  }
  return value;
}

test('log in through Auth0 and create a report', async ({page}) => {
  const homePage = new HomePage(page);
  const reportPage = new ReportPage(page);

  await page.goto('/');
  await page.getByRole('textbox', {name: /email/i}).fill(requireEnv('AUTH0_USEREMAIL'));
  await page.getByRole('button', {name: 'Continue', exact: true}).click();
  await page
    .getByRole('textbox', {name: 'Password', exact: true})
    .fill(requireEnv('AUTH0_USERPASSWORD'));
  await page.getByRole('button', {name: 'Continue', exact: true}).click();
  await expect(page.getByRole('navigation', {name: 'Main navigation'})).toBeVisible({
    timeout: LOGIN_TIMEOUT,
  });

  await homePage.goto();
  await homePage.createNew('Report');
  const dialog = new TemplateDialog(page, 'Create new report');
  await dialog.selectTemplate('Blank report');
  await dialog.confirm();
  await reportPage.save();
  await expect(reportPage.heading).toHaveText('Blank report');

  await reportPage.delete();
});
