/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createSingleInstance, createWorker} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';
import {completeUserTask, findUserTask} from '@requestHelpers';
import {waitForAssertion} from 'utils/waitForAssertion';

let initialData: {
  processWithListenerInstance: {processInstanceKey: string};
  processWithListenerOnRootInstance: {processInstanceKey: string};
  userTaskProcessInstance: {processInstanceKey: string};
};

test.beforeAll(async () => {
  // Deploy processes and create instances
  await deploy(['./resources/processWithListener.bpmn']);
  const processWithListenerInstance = await createSingleInstance(
    'processWithListener',
    1,
  );

  await deploy(['./resources/processWithListenerOnRoot.bpmn']);
  createWorker('processStartListener', false);
  const processWithListenerOnRootInstance = await createSingleInstance(
    'processWithListenerOnRoot',
    1,
  );

  await deploy(['./resources/processWithUserTaskListener.bpmn']);
  const userTaskProcessInstance = await createSingleInstance(
    'processWithUserTaskListener',
    1,
  );
  createWorker('completeListener', false);
  createWorker('endListener', false);

  initialData = {
    processWithListenerInstance,
    processWithListenerOnRootInstance,
    userTaskProcessInstance,
  };

  // Wait for deployment to be processed
  await sleep(3000);
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

  test('Listeners tab should always be visible', async ({
    operateProcessInstancePage,
  }) => {
    const processInstanceKey =
      initialData.processWithListenerInstance.processInstanceKey;

    await test.step('Navigate to process instance page', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: processInstanceKey,
      });

      await operateProcessInstancePage.verifyListenersTabVisible();
    });

    await test.step('Verify listeners tab is visible when selecting start event', async () => {
      await operateProcessInstancePage.clickInstanceHistoryElement(
        'StartEvent_1',
      );
      await expect(operateProcessInstancePage.listenersTabButton).toBeVisible();
    });

    await test.step('Verify listeners tab is visible when selecting service task', async () => {
      await operateProcessInstancePage.clickInstanceHistoryElement(
        /service task b/i,
      );
      await expect(operateProcessInstancePage.listenersTabButton).toBeVisible();
    });
  });

  test('Listeners data displayed', async ({operateProcessInstancePage}) => {
    const processInstanceKey =
      initialData.processWithListenerInstance.processInstanceKey;

    await test.step('Navigate to process instance and select service task', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: processInstanceKey,
      });

      await operateProcessInstancePage.clickInstanceHistoryElement(
        /service task b/i,
      );
    });

    await test.step('Open listeners tab and verify execution listener is displayed', async () => {
      await operateProcessInstancePage.openListenersTab();
      await expect(
        operateProcessInstancePage.executionListenerText,
      ).toBeVisible();
    });
  });

  test('Listeners list filtered by flow node instance', async ({
    operateProcessInstancePage,
    page,
  }) => {
    const processInstanceKey =
      initialData.processWithListenerInstance.processInstanceKey;

    await test.step('Navigate to process instance and verify initial listener count', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: processInstanceKey,
      });

      await operateProcessInstancePage.diagramHelper.clickFlowNode(
        'Service Task B',
      );
      await operateProcessInstancePage.openListenersTab();
      await expect(
        operateProcessInstancePage.getListenerRows('execution'),
      ).toHaveCount(1);
    });

    await test.step('Add a new flow node instance', async () => {
      await operateProcessInstancePage.startModificationFlow();
      await operateProcessInstancePage.diagramHelper.clickFlowNode(
        'Service Task B',
      );
      await operateProcessInstancePage.addSingleFlowNodeInstanceButton.click();
      await operateProcessInstancePage.applyModifications();
    });

    await test.step('Wait for new instance to appear and verify listener count', async () => {
      await expect
        .poll(async () => {
          return operateProcessInstancePage.instanceHistory
            .getByText('Service Task B')
            .count();
        })
        .toBe(2);

      await operateProcessInstancePage.diagramHelper.clickFlowNode(
        'Service Task B',
      );
      await operateProcessInstancePage.openListenersTab();

      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessInstancePage.getListenerRows('execution'),
          ).toHaveCount(2, {timeout: 5000});
        },
        onFailure: async () => {
          await page.reload();
          await operateProcessInstancePage.diagramHelper.clickFlowNode(
            'Service Task B',
          );
          await operateProcessInstancePage.openListenersTab();
        },
      });
    });

    await test.step('Select specific flow node instance and verify single listener', async () => {
      await operateProcessInstancePage
        .getInstanceHistoryElement('Service Task B')
        .first()
        .click();
      await operateProcessInstancePage.openListenersTab();
      await expect(
        operateProcessInstancePage.getListenerRows('execution'),
      ).toHaveCount(1);
    });
  });

  test('Listeners list filtered by listener type', async ({
    page,
    request,
    operateProcessInstancePage,
  }) => {
    const processInstanceKey =
      initialData.userTaskProcessInstance.processInstanceKey;
    const userTaskKeyRegex = new RegExp('\\d{16}');
    let userTaskKey = '';

    await test.step('Navigate to process instance and get user task key', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: processInstanceKey,
      });

      await expect(
        operateProcessInstancePage.diagramHelper.getFlowNode('Service Task B'),
      ).toBeVisible();
      await operateProcessInstancePage.diagramHelper.clickFlowNode(
        'Service Task B',
      );

      userTaskKey = await findUserTask(request, processInstanceKey, 'CREATED');

      expect(userTaskKey).toMatch(userTaskKeyRegex);
      await expect(operateProcessInstancePage.stateOverlayActive).toBeVisible();
    });

    await test.step('Complete the user task', async () => {
      const completeRes = await completeUserTask(request, userTaskKey);
      expect(completeRes.status()).toBe(204);

      await expect(operateProcessInstancePage.stateOverlayActive).toBeHidden();
      await expect(
        operateProcessInstancePage.stateOverlayCompletedEndEvents,
      ).toBeVisible();
      await sleep(1000);
      await operateProcessInstancePage.clickInstanceHistoryElement(
        'Service Task B',
      );
      await operateProcessInstancePage.diagramHelper.moveCanvasHorizontally(
        -400,
      );
    });

    await test.step('Verify both listener types are visible by default', async () => {
      await operateProcessInstancePage.openListenersTab();
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessInstancePage.getListenerRows('execution'),
          ).toHaveCount(1, {timeout: 5000});
          await expect(
            operateProcessInstancePage.getListenerRows('task'),
          ).toHaveCount(1, {timeout: 5000});
        },
        onFailure: async () => {
          await page.reload();
          await operateProcessInstancePage.diagramHelper.clickFlowNode(
            'Service Task B',
          );
          await operateProcessInstancePage.openListenersTab();
        },
      });
      await expect(
        operateProcessInstancePage.executionListenerText,
      ).toBeVisible();
      await expect(operateProcessInstancePage.taskListenerText).toBeVisible();
    });

    await test.step('Filter to show only execution listeners', async () => {
      await operateProcessInstancePage.listenerTypeFilter.click();
      await operateProcessInstancePage
        .getListenerTypeFilterOption('Execution listeners')
        .click();
      await expect(
        operateProcessInstancePage.getExecutionListenerText(true),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getTaskListenerText(true),
      ).toBeHidden();
    });

    await test.step('Filter to show only user task listeners', async () => {
      await operateProcessInstancePage.listenerTypeFilter.click();
      await operateProcessInstancePage
        .getListenerTypeFilterOption('User task listeners')
        .click();
      await expect(
        operateProcessInstancePage.getTaskListenerText(true),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getExecutionListenerText(true),
      ).toBeHidden();
    });

    await test.step('Filter to show all listeners', async () => {
      await operateProcessInstancePage.listenerTypeFilter.click();
      await operateProcessInstancePage
        .getListenerTypeFilterOption('All listeners')
        .click();
      await expect(
        operateProcessInstancePage.getExecutionListenerText(true),
      ).toBeVisible();
      await expect(
        operateProcessInstancePage.getTaskListenerText(true),
      ).toBeVisible();
    });
  });

  test('Listeners on process instance or participant (root flow node)', async ({
    operateProcessInstancePage,
  }) => {
    const processInstanceKey =
      initialData.processWithListenerOnRootInstance.processInstanceKey;

    await test.step('Navigate to process instance and modify it', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: processInstanceKey,
      });

      await operateProcessInstancePage.startModificationFlow();
      await operateProcessInstancePage.diagramHelper.clickFlowNode(
        'Service Task B',
      );
      await operateProcessInstancePage.moveSelectedInstanceButton.click({
        timeout: 30000,
      });
      await operateProcessInstancePage.diagramHelper.clickEvent('EndEvent');
      await operateProcessInstancePage.applyModifications();
    });

    await test.step('Select root process node and verify listeners tab visibility', async () => {
      await expect
        .poll(async () => {
          await operateProcessInstancePage.clickInstanceHistoryElement(
            /Start event/i,
          );
          await operateProcessInstancePage.clickInstanceHistoryElement(
            /processWithListenerOnRoot/i,
          );

          try {
            await expect(
              operateProcessInstancePage.listenersTabButton,
            ).toBeVisible({
              timeout: 500,
            });
          } catch {
            return false;
          }
          return true;
        })
        .toBe(true);
    });

    await test.step('Open listeners tab and verify execution listener is displayed', async () => {
      await operateProcessInstancePage.openListenersTab();
      await expect(
        operateProcessInstancePage.executionListenerText,
      ).toBeVisible();
    });
  });
});
