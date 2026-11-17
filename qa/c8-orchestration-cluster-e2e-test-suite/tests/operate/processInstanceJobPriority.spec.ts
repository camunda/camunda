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
import {navigateToAppHome} from '@pages/UtilitiesPage';

type ProcessInstance = {
  processInstanceKey: string;
};

let instanceWithExplicitPriority: ProcessInstance;
let instanceWithDefaultPriority: ProcessInstance;

test.beforeAll(async () => {
  await deploy([
    './resources/simpleServiceTaskWithPriorityProcess.bpmn',
    './resources/simpleServiceTaskProcess.bpmn',
  ]);

  instanceWithExplicitPriority = await createSingleInstance(
    'simpleServiceTaskWithPriorityProcess',
    1,
  );

  instanceWithDefaultPriority = await createSingleInstance(
    'simpleServiceTaskProcess',
    1,
  );
});

test.describe('Process Instance Job Priority', () => {
  test.beforeEach(async ({page, operateHomePage}) => {
    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Job Priority row shows the value defined in the process model', async ({
    operateProcessInstancePage,
  }) => {
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: instanceWithExplicitPriority.processInstanceKey,
    });

    await operateProcessInstancePage.clickTreeItem(/priority task/i);
    await operateProcessInstancePage.clickDetailsTab();

    await expect(operateProcessInstancePage.jobPriorityValue).toHaveText('42');
  });

  test('Job Priority row shows the default priority when none is defined', async ({
    operateProcessInstancePage,
  }) => {
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: instanceWithDefaultPriority.processInstanceKey,
    });

    await operateProcessInstancePage.clickTreeItem('task', true);
    await operateProcessInstancePage.clickDetailsTab();

    await expect(operateProcessInstancePage.jobPriorityValue).toHaveText('0');
  });
});
