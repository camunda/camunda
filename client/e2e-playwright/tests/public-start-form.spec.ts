/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from '../axe-test';
import * as zeebeClient from '../zeebeClient';

test.describe('public start process', () => {
  test('should submit form', async ({page, makeAxeBuilder}) => {
    await zeebeClient.deploy([
      './e2e-playwright/resources/subscribeFormProcess.bpmn',
    ]);
    await page.goto('/new/subscribeFormProcess');

    expect(page.getByLabel('Name').isVisible()).toBeTruthy();

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);

    await page.getByLabel('Name').fill('Joe Doe');
    await page.getByLabel('Email').fill('joe@doe.com');
    await page.getByRole('button', {name: 'Save'}).click();

    await expect(page.getByText('Success')).toBeVisible();
  });
});
