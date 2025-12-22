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
import {sleep} from 'utils/sleep';

type ProcessDeployment = {
  readonly processInstanceKey: string;
};

let callActivityProcessInstance: ProcessDeployment;

test.beforeAll(async () => {
  await deploy([
    './resources/callActivityProcess.bpmn',
    './resources/calledProcess.bpmn',
  ]);

  const instance = await createSingleInstance('CallActivityProcess', 1);
  callActivityProcessInstance = {
    processInstanceKey: instance.processInstanceKey,
  };

  await sleep(2000);
});

test.describe('Call Activities', () => {
  test.describe.configure({retries: 0});

  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Navigate to called and parent process instances', async ({
    operateProcessInstancePage,
    operateProcessesPage,
    operateDiagramPage,
  }) => {
    test.slow();
    const processInstanceKey = callActivityProcessInstance.processInstanceKey;

    await test.step('Navigate to the call activity process instance', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: processInstanceKey,
      });

      await expect(
        operateProcessInstancePage.instanceHeaderSkeleton,
      ).toBeHidden();
      await expect(operateProcessInstancePage.instanceHeader).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHeader.getByText(processInstanceKey),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHeader.getByText(
          'Call Activity Process',
        ),
      ).toBeVisible();
    });

    await test.step('View all called instances', async () => {
      await operateProcessInstancePage.clickViewAllCalledInstances();

      await expect(operateProcessesPage.processInstancesTable).toHaveCount(1);
      await expect(
        operateProcessesPage.processInstancesTable.getByText('Called Process'),
      ).toBeVisible();
    });

    let calledProcessInstanceId: string;

    await test.step('Get called process instance ID', async () => {
      calledProcessInstanceId = await operateProcessesPage
        .calledInstanceCell()
        .innerText();
    });

    await test.step('Navigate back to parent instance from list', async () => {
      await operateProcessesPage.clickViewParentInstanceFromList();

      await expect(
        operateProcessInstancePage.instanceHeader.getByText(processInstanceKey),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHeader.getByText(
          'Call Activity Process',
        ),
      ).toBeVisible();
    });

    await test.step('Verify instance history on parent process', async () => {
      await expect(
        operateProcessInstancePage.instanceHistory.getByText(
          'Call Activity Process',
        ),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHistory.getByText('StartEvent_1'),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHistory.getByText('Call Activity', {
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHistory.getByText('Event_1p0nsc7'),
      ).toBeVisible();
    });

    await test.step('Verify diagram shows call activity', async () => {
      await expect(
        operateDiagramPage.getFlowNode('Activity_13otele'),
      ).toBeVisible();
    });

    await test.step('Navigate to called process instance via diagram', async () => {
      await operateDiagramPage.clickFlowNode('Activity_13otele');

      await expect(
        operateDiagramPage.getPopoverText(/Called Process Instance/),
      ).toBeVisible();

      await operateDiagramPage.clickPopoverLink(
        /view called process instance/i,
      );
    });

    await test.step('Verify called process instance header and history', async () => {
      await expect(
        operateProcessInstancePage.instanceHeader.getByText(
          calledProcessInstanceId,
        ),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHeader.getByText('Called Process', {
          exact: true,
        }),
      ).toBeVisible();

      await expect(
        operateProcessInstancePage.instanceHistory.getByText('Called Process', {
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHistory.getByText('Process started'),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHistory.getByText('Event_0y6k56d'),
      ).toBeVisible();
    });

    await test.step('Verify called process diagram', async () => {
      await expect(
        operateDiagramPage.getFlowNode('StartEvent_1'),
      ).toBeVisible();
    });

    await test.step('Navigate back to parent instance from header', async () => {
      await operateProcessInstancePage.clickViewParentInstance();

      await expect(
        operateProcessInstancePage.instanceHeader.getByText(processInstanceKey),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHeader.getByText(
          'Call Activity Process',
        ),
      ).toBeVisible();
    });

    await test.step('Verify parent process instance history and diagram again', async () => {
      await expect(
        operateProcessInstancePage.instanceHistory.getByText(
          'Call Activity Process',
        ),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHistory.getByText('StartEvent_1'),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHistory.getByText('Call Activity', {
          exact: true,
        }),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHistory.getByText('Event_1p0nsc7'),
      ).toBeVisible();

      await expect(
        operateDiagramPage.getFlowNode('Activity_13otele'),
      ).toBeVisible();
    });
  });
});
