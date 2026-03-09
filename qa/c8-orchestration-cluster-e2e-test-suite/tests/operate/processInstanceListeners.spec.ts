/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createSingleInstance} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {waitForProcessInstances} from 'utils/incidentsHelper';

let processInstanceKey: string;

test.beforeAll(async ({request}) => {
  await deploy(['./resources/processWithListener.bpmn']);

  const instance = await createSingleInstance('processWithListener', 1);
  processInstanceKey = String(instance.processInstanceKey);

  await waitForProcessInstances(request, [processInstanceKey], 1);
});

test.describe('Process Instance Listeners', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Listeners tab button show/hide', async ({
    operateProcessInstancePage,
  }) => {
    await test.step('Navigate to process instance', async () => {
      await operateProcessInstancePage.navigateToProcessInstance(
        processInstanceKey,
      );
    });

    await test.step('Start flow node should NOT show listeners tab', async () => {
      await operateProcessInstancePage.startFlowNode.click();
      await expect(operateProcessInstancePage.listenersTabButton).toBeHidden();
    });

    await test.step('Service task flow node should show listeners tab', async () => {
      await operateProcessInstancePage.serviceTaskBFlowNode.click();
      await expect(operateProcessInstancePage.listenersTabButton).toBeVisible();
    });
  });

  test('Listeners data displayed', async ({operateProcessInstancePage}) => {
    await test.step('Navigate to process instance', async () => {
      await operateProcessInstancePage.navigateToProcessInstance(
        processInstanceKey,
      );
    });

    await test.step('Click service task and open listeners tab', async () => {
      await operateProcessInstancePage.serviceTaskBFlowNode.click();
      await operateProcessInstancePage.listenersTabButton.click();
    });

    await test.step('Verify execution listener is displayed', async () => {
      await expect(
        operateProcessInstancePage.executionListenerText,
      ).toBeVisible();
    });
  });
});
