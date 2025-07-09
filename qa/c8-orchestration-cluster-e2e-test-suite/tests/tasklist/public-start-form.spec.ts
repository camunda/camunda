/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {deploy} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {sleep} from 'utils/sleep';

test.describe('public start process', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('should submit form', async ({makeAxeBuilder, publicFormsPage}) => {
    await deploy([
      './resources/subscribeFormProcess.bpmn',
      './resources/subscribeForm.form',
    ]);
    await sleep(500);
    await publicFormsPage.goToPublicForm('subscribeFormProcess');

    await expect(publicFormsPage.nameInput).toBeVisible({timeout: 60000});

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);

    await publicFormsPage.nameInput.fill('Joe Doe');
    await publicFormsPage.emailInput.fill('joe@doe.com');
    await publicFormsPage.submitButton.click();

    await expect(publicFormsPage.successMessage).toBeVisible();
  });
});
