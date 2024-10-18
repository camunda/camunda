/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../test-fixtures';
import {expect} from '@playwright/test';

test.describe.only('Test', () => {
  test('Test 1', async ({page, processesPage}) => {
    await processesPage.navigateToProcesses({
      searchParams: {active: 'true', incidents: 'true'},
    });

    await page.screenshot({path: '1.png'});
    console.log('screenshot 1');

    await expect(page.getByText('Running instances')).toBeVisible({
      timeout: 5000,
    });
  });

  test('Test 2', async ({page}) => {
    await page.goto(
      'http://localhost:8080/operate/processes?active=true&incidents=true',
    );

    await page.screenshot({path: '2.png'});
    console.log('screenshot 2');

    await expect(page.getByText('Running instances')).toBeVisible({
      timeout: 5000,
    });
  });

  test('Test 3', async ({page}) => {
    await page.goto(
      'http://localhost:8080/processes?active=true&incidents=true',
    );

    await page.screenshot({path: '3.png'});
    console.log('screenshot 3');

    await expect(page.getByText('Running instances')).toBeVisible({
      timeout: 5000,
    });
  });
});
