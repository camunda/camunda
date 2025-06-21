/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../visual-fixtures';
import {validateResults} from './validateResults';
import {clientConfigMock} from '../mocks/clientConfig';
import {expect} from '@playwright/test';

test.beforeEach(async ({context}) => {
  await context.route('**/client-config.js', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/javascript;charset=UTF-8',
      },
      body: clientConfigMock,
    }),
  );
});

test.describe('login', () => {
  test(`have no violations`, async ({page, loginPage, makeAxeBuilder}) => {
    await loginPage.gotoLoginPage();

    await expect(page.getByRole('heading', {name: 'Operate'})).toBeVisible();

    const results = await makeAxeBuilder().analyze();

    validateResults(results);
  });
});
