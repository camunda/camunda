/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {
  deploy,
  createSingleInstance,
  cancelProcessInstance,
} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToAppHome} from '@pages/UtilitiesPage';

let processInstanceKey: string | undefined;

test.beforeAll(async () => {
  await deploy(['./resources/user_process.bpmn']);
  const instance = await createSingleInstance('User_Process', 1);
  processInstanceKey = instance.processInstanceKey;
});

test.afterAll(async () => {
  if (processInstanceKey) {
    await cancelProcessInstance(processInstanceKey);
  }
});

test.describe('Process Instance Tasklist Link', () => {
  test.beforeEach(async ({page, operateHomePage}) => {
    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Details tab links a Camunda user task to Tasklist without extra configuration', async ({
    operateProcessInstancePage,
  }) => {
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: processInstanceKey!,
    });

    await operateProcessInstancePage.clickTreeItem(/user_task/i);
    await operateProcessInstancePage.clickDetailsTab();

    await expect(operateProcessInstancePage.openTasklistLink).toHaveAttribute(
      'href',
      /\/tasklist\/\d+\?filter=all-open$/,
    );
  });
});
