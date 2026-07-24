/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {cancelProcessInstance, setVariables} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {waitForAssertion} from 'utils/waitForAssertion';
import {buildUrl, jsonHeaders, assertStatusCode} from 'utils/http';
import {
  createProcessInstanceWaitingOnJob,
  createProcessInstanceWaitingOnMessage,
  createProcessInstanceWaitingOnSignal,
  createProcessInstanceWaitingOnUserTask,
  createProcessInstanceWaitingOnTimer,
  createProcessInstanceWaitingOnCondition,
  activateJobsByType,
  completeJob,
  completeUserTask,
  findUserTask,
} from '@requestHelpers';

let jobInstance: {processInstanceKey: string};
let messageInstance: {processInstanceKey: string};
let signalInstance: {processInstanceKey: string};
let userTaskInstance: {processInstanceKey: string};
let timerInstance: {processInstanceKey: string};
let conditionInstance: {processInstanceKey: string};

const persistentInstanceKeys: string[] = [];

test.beforeAll(async () => {
  jobInstance = await createProcessInstanceWaitingOnJob();
  messageInstance = await createProcessInstanceWaitingOnMessage();
  signalInstance = await createProcessInstanceWaitingOnSignal();
  userTaskInstance = await createProcessInstanceWaitingOnUserTask();
  // Long duration — this instance is only used to assert the Details tab
  // renders correctly; it must not fire mid-suite.
  timerInstance = await createProcessInstanceWaitingOnTimer('PT1H');
  conditionInstance = await createProcessInstanceWaitingOnCondition();

  persistentInstanceKeys.push(
    jobInstance.processInstanceKey,
    messageInstance.processInstanceKey,
    signalInstance.processInstanceKey,
    userTaskInstance.processInstanceKey,
    timerInstance.processInstanceKey,
    conditionInstance.processInstanceKey,
  );
});

test.afterAll(async () => {
  for (const key of persistentInstanceKeys) {
    await cancelProcessInstance(key);
  }
});

test.describe('Wait State Details Tab', () => {
  test.beforeEach(async ({page, operateHomePage}) => {
    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('shows the JOB wait reason without crashing', async ({
    operateProcessInstancePage,
  }) => {
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: jobInstance.processInstanceKey,
    });
    await operateProcessInstancePage.clickTreeItem('task', true);
    await operateProcessInstancePage.clickDetailsTab();
    await expect(operateProcessInstancePage.waitingStatus).toBeVisible();
  });

  test('shows the MESSAGE wait reason without crashing', async ({
    operateProcessInstancePage,
  }) => {
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: messageInstance.processInstanceKey,
    });
    await operateProcessInstancePage.clickTreeItem('Event_1idbbd5', true);
    await operateProcessInstancePage.clickDetailsTab();
    await expect(operateProcessInstancePage.waitingStatus).toBeVisible();
  });

  test('shows the SIGNAL wait reason without crashing', async ({
    operateProcessInstancePage,
  }) => {
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: signalInstance.processInstanceKey,
    });
    await operateProcessInstancePage.clickTreeItem(/receive test signal/i);
    await operateProcessInstancePage.clickDetailsTab();
    await expect(operateProcessInstancePage.waitingStatus).toBeVisible();
  });

  test('shows the USER_TASK wait reason without crashing', async ({
    operateProcessInstancePage,
  }) => {
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: userTaskInstance.processInstanceKey,
    });
    await operateProcessInstancePage.clickTreeItem(/test user task api/i);
    await operateProcessInstancePage.clickDetailsTab();
    await expect(operateProcessInstancePage.waitingStatus).toBeVisible();
  });

  test('shows the TIMER wait reason without crashing', async ({
    operateProcessInstancePage,
  }) => {
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: timerInstance.processInstanceKey,
    });
    await operateProcessInstancePage.clickTreeItem(/wait for timer/i);
    await operateProcessInstancePage.clickDetailsTab();
    await expect(operateProcessInstancePage.waitingStatus).toBeVisible();
  });

  test('shows the CONDITIONAL wait reason without crashing', async ({
    operateProcessInstancePage,
  }) => {
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: conditionInstance.processInstanceKey,
    });
    await operateProcessInstancePage.clickTreeItem(/wait for condition/i);
    await operateProcessInstancePage.clickDetailsTab();
    await expect(operateProcessInstancePage.waitingStatus).toBeVisible();
  });

  test('does not show a wait reason for a completed, non-waiting element', async ({
    operateProcessInstancePage,
  }) => {
    await operateProcessInstancePage.gotoProcessInstancePage({
      id: jobInstance.processInstanceKey,
    });
    await operateProcessInstancePage.clickTreeItem('StartEvent_1', true);
    await operateProcessInstancePage.clickDetailsTab();
    await expect(operateProcessInstancePage.instanceHeader).toBeVisible();
    await expect(operateProcessInstancePage.waitingStatus).toBeHidden();
  });
});

test.describe('Wait State Lifecycle', () => {
  test.beforeEach(async ({page, operateHomePage}) => {
    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('JOB wait indicator clears once the job is completed', async ({
    page,
    request,
    operateProcessInstancePage,
    operateDiagramPage,
  }) => {
    const instance = await createProcessInstanceWaitingOnJob();

    await operateProcessInstancePage.gotoProcessInstancePage({
      id: instance.processInstanceKey,
    });
    await expect(operateDiagramPage.getWaitingOverlay('task')).toBeVisible({
      timeout: 30_000,
    });

    const jobs = await activateJobsByType(
      request,
      'task',
      instance.processInstanceKey,
    );
    expect(jobs.length).toBe(1);
    await completeJob(request, jobs[0].jobKey);

    await waitForAssertion({
      assertion: async () => {
        await expect(operateDiagramPage.getWaitingOverlay('task')).toBeHidden();
      },
      onFailure: async () => {
        await page.reload();
      },
    });
  });

  test('MESSAGE wait indicator clears once the message is published', async ({
    page,
    request,
    operateProcessInstancePage,
    operateDiagramPage,
  }) => {
    const instance = await createProcessInstanceWaitingOnMessage();

    await operateProcessInstancePage.gotoProcessInstancePage({
      id: instance.processInstanceKey,
    });
    await expect(
      operateDiagramPage.getWaitingOverlay('Event_1idbbd5'),
    ).toBeVisible({timeout: 30_000});

    const res = await request.post(buildUrl('/messages/publication'), {
      headers: jsonHeaders(),
      data: {name: 'Message_143t419', correlationKey: '143419'},
    });
    await assertStatusCode(res, 200);

    await waitForAssertion({
      assertion: async () => {
        await expect(
          operateDiagramPage.getWaitingOverlay('Event_1idbbd5'),
        ).toBeHidden();
      },
      onFailure: async () => {
        await page.reload();
      },
    });
  });

  test('SIGNAL wait indicator clears once the signal is broadcast', async ({
    page,
    request,
    operateProcessInstancePage,
    operateDiagramPage,
  }) => {
    const instance = await createProcessInstanceWaitingOnSignal();

    await operateProcessInstancePage.gotoProcessInstancePage({
      id: instance.processInstanceKey,
    });
    await expect(
      operateDiagramPage.getWaitingOverlay('Event_Test_Signal'),
    ).toBeVisible({timeout: 30_000});

    const res = await request.post(buildUrl('/signals/broadcast'), {
      headers: jsonHeaders(),
      data: {signalName: 'Signal_220k2ur'},
    });
    await assertStatusCode(res, 200);

    await waitForAssertion({
      assertion: async () => {
        await expect(
          operateDiagramPage.getWaitingOverlay('Event_Test_Signal'),
        ).toBeHidden();
      },
      onFailure: async () => {
        await page.reload();
      },
    });
  });

  test('USER_TASK wait indicator clears once the user task is completed', async ({
    page,
    request,
    operateProcessInstancePage,
    operateDiagramPage,
  }) => {
    const instance = await createProcessInstanceWaitingOnUserTask();

    await operateProcessInstancePage.gotoProcessInstancePage({
      id: instance.processInstanceKey,
    });
    await expect(
      operateDiagramPage.getWaitingOverlay('test_user_task_api'),
    ).toBeVisible({timeout: 30_000});

    const userTaskKey = await findUserTask(
      request,
      instance.processInstanceKey,
      'CREATED',
    );
    const completeRes = await completeUserTask(request, userTaskKey);
    expect(completeRes.status()).toBe(204);

    await waitForAssertion({
      assertion: async () => {
        await expect(
          operateDiagramPage.getWaitingOverlay('test_user_task_api'),
        ).toBeHidden();
      },
      onFailure: async () => {
        await page.reload();
      },
    });
  });

  test('TIMER wait indicator clears once the timer fires', async ({
    page,
    operateProcessInstancePage,
    operateDiagramPage,
  }) => {
    const instance = await createProcessInstanceWaitingOnTimer('PT2S');

    await operateProcessInstancePage.gotoProcessInstancePage({
      id: instance.processInstanceKey,
    });
    await expect(
      operateDiagramPage.getWaitingOverlay('timer-catch'),
    ).toBeVisible({timeout: 30_000});

    await waitForAssertion({
      assertion: async () => {
        await expect(
          operateDiagramPage.getWaitingOverlay('timer-catch'),
        ).toBeHidden();
      },
      onFailure: async () => {
        await page.reload();
      },
      maxRetries: 6,
    });
  });

  test('CONDITIONAL wait indicator clears once the condition is met', async ({
    page,
    operateProcessInstancePage,
    operateDiagramPage,
  }) => {
    const instance = await createProcessInstanceWaitingOnCondition();

    await operateProcessInstancePage.gotoProcessInstancePage({
      id: instance.processInstanceKey,
    });
    await expect(
      operateDiagramPage.getWaitingOverlay('conditional-catch'),
    ).toBeVisible({timeout: 30_000});

    await setVariables(instance.processInstanceKey, {x: 42});

    await waitForAssertion({
      assertion: async () => {
        await expect(
          operateDiagramPage.getWaitingOverlay('conditional-catch'),
        ).toBeHidden();
      },
      onFailure: async () => {
        await page.reload();
      },
    });
  });

  test('wait indicator disappears when the instance is canceled while waiting', async ({
    page,
    operateProcessInstancePage,
    operateDiagramPage,
  }) => {
    const instance = await createProcessInstanceWaitingOnJob();

    await operateProcessInstancePage.gotoProcessInstancePage({
      id: instance.processInstanceKey,
    });
    await expect(operateDiagramPage.getWaitingOverlay('task')).toBeVisible({
      timeout: 30_000,
    });

    await cancelProcessInstance(instance.processInstanceKey);

    await waitForAssertion({
      assertion: async () => {
        await expect(operateDiagramPage.getWaitingOverlay('task')).toBeHidden();
      },
      onFailure: async () => {
        await page.reload();
      },
    });
  });
});
