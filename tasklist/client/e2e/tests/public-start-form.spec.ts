/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import * as zeebeClient from '../zeebeClient';

test.describe('public start process', () => {
  test('should submit form', async ({makeAxeBuilder, publicFormsPage}) => {
    await zeebeClient.deploy(['./e2e/resources/subscribeFormProcess.bpmn']);
    await publicFormsPage.goToPublicForm('subscribeFormProcess');

    await expect(publicFormsPage.nameInput).toBeVisible();

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);

    await publicFormsPage.nameInput.fill('Joe Doe');
    await publicFormsPage.emailInput.fill('joe@doe.com');
    await publicFormsPage.clickSubmitButton();

    await expect(publicFormsPage.successMessage).toBeVisible();
  });
});
