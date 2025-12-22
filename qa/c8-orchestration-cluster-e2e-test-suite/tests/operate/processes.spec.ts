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
import {waitForAssertion} from 'utils/waitForAssertion';
import {sleep} from 'utils/sleep';

type ProcessInstance = {
  processInstanceKey: string;
};
type DeployedProcess = {
  version: number;
  bpmnProcessId: string;
};

let instanceWithoutAnIncident: ProcessInstance;
let instanceWithAnIncident: ProcessInstance;
let instanceToCancel: ProcessInstance;
let deployedProcess: DeployedProcess | undefined;

test.beforeAll(async () => {
  await deploy(['./resources/orderProcess_processes.bpmn']);
  deployedProcess = {
    version: 1,
    bpmnProcessId: 'orderProcessProcessesTest',
  };

  const instanceWithoutAnIncidentResponse = await createSingleInstance(
    'orderProcessProcessesTest',
    1,
  );
  instanceWithoutAnIncident = {
    processInstanceKey: instanceWithoutAnIncidentResponse.processInstanceKey,
  };

  await deploy(['./resources/processWithAnIncident.bpmn']);

  const instanceWithAnIncidentResponse = await createSingleInstance(
    'processWithAnIncident',
    1,
  );
  instanceWithAnIncident = {
    processInstanceKey: instanceWithAnIncidentResponse.processInstanceKey,
  };

  await deploy(['./resources/process_to_test_delete_process_definition.bpmn']);

  const instanceToCancelResponse = await createSingleInstance(
    'process_to_test_delete_process_definition',
    1,
  );
  instanceToCancel = {
    processInstanceKey: instanceToCancelResponse.processInstanceKey,
  };

  await sleep(2000);
});

test.describe('Processes', () => {
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

  test('Processes Page Initial Load', async ({
    operateProcessesPage,
    operateFiltersPanelPage,
    page,
  }) => {
    await test.step('Validate initial filter state', async () => {
      await expect(
        operateFiltersPanelPage.runningInstancesCheckbox,
      ).toBeChecked();
      await expect(
        operateFiltersPanelPage.activeInstancesCheckbox,
      ).toBeChecked();
      await expect(
        operateFiltersPanelPage.incidentsInstancesCheckbox,
      ).toBeChecked();
      await expect(
        operateFiltersPanelPage.finishedInstancesCheckbox,
      ).not.toBeChecked();
      await expect(
        operateFiltersPanelPage.completedInstancesCheckbox,
      ).not.toBeChecked();
      await expect(
        operateFiltersPanelPage.canceledInstancesCheckbox,
      ).not.toBeChecked();
    });

    await test.step('Validate no process selected message', async () => {
      await expect(
        page.getByText('There is no Process selected'),
      ).toBeVisible();
      await expect(
        page.getByText(
          'To see a Diagram, select a Process in the filters panel',
        ),
      ).toBeVisible();
    });

    await test.step('Filter by process instance keys and validate results', async () => {
      await operateFiltersPanelPage.displayOptionalFilter(
        'Process Instance Key(s)',
      );

      await operateFiltersPanelPage.processInstanceKeysFilter.fill(
        `${instanceWithoutAnIncident.processInstanceKey}, ${instanceWithAnIncident.processInstanceKey}`,
      );

      await expect(operateProcessesPage.dataList).toBeVisible();
      await expect(operateProcessesPage.processInstancesTable).toHaveCount(2);

      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessesPage.dataList.getByTestId(
              `INCIDENT-icon-${instanceWithAnIncident.processInstanceKey}`,
            ),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await expect(
        operateProcessesPage.dataList.getByTestId(
          `ACTIVE-icon-${instanceWithoutAnIncident.processInstanceKey}`,
        ),
      ).toBeVisible();
    });
  });

  test('Select flow node in diagram', async ({
    operateProcessesPage,
    operateFiltersPanelPage,
    page,
    operateDiagramPage,
  }) => {
    const version = deployedProcess!.version.toString();

    await test.step('Navigate to processes page with filters', async () => {
      await operateFiltersPanelPage.displayOptionalFilter(
        'Process Instance Key(s)',
      );
      await operateFiltersPanelPage.processInstanceKeysFilter.fill(
        instanceWithoutAnIncident.processInstanceKey,
      );

      await operateFiltersPanelPage.selectProcess(
        'Order process processes test',
      );
      await waitForAssertion({
        assertion: async () => {
          await expect
            .poll(() =>
              operateFiltersPanelPage.processVersionFilter.innerText(),
            )
            .toBe(version);
        },
        onFailure: async () => {
          await page.reload();
          await operateFiltersPanelPage.selectProcess(
            'Order process processes test',
          );
        },
      });

      await expect(operateDiagramPage.diagram).toBeInViewport();
    });

    await test.step('Select Ship Articles flow node', async () => {
      await operateDiagramPage.clickFlowNode('shipArticles');
      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue(
        'Ship Articles',
      );

      await expect(
        operateProcessesPage.noMatchingInstancesMessage,
      ).toBeVisible();

      await expect(page).toHaveURL(/flowNodeId=shipArticles/);
    });

    await test.step('Ensure Check Payment flow node is not selected', async () => {
      await expect(
        operateDiagramPage.getFlowNode('checkPayment'),
      ).not.toHaveClass(/selected/);
    });

    await test.step('Select Check Payment flow node', async () => {
      await operateDiagramPage.clickFlowNode('checkPayment');
      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue(
        'Check payment',
      );

      await expect(operateProcessesPage.processInstancesTable).toHaveCount(1);

      await expect(page).toHaveURL(/flowNodeId=checkPayment/);

      await expect(operateDiagramPage.getFlowNode('checkPayment')).toHaveClass(
        /selected/,
      );
    });

    await test.step('Verify flow node selection persists after reload', async () => {
      await page.reload();
      await expect(operateDiagramPage.getFlowNode('checkPayment')).toHaveClass(
        /selected/,
      );
    });
  });

  test('Wait for process creation', async ({
    operateProcessesPage,
    operateFiltersPanelPage,
    operateDiagramPage,
    page,
  }) => {
    await test.step('Navigate to non-existent process', async () => {
      const baseUrl =
        process.env.CORE_APPLICATION_URL || 'http://localhost:8080';
      await page.goto(
        `${baseUrl}/operate/processes?process=testProcess&version=1`,
      );

      await expect(operateFiltersPanelPage.processNameFilter).toBeDisabled({
        timeout: 30000,
      });

      await expect(operateDiagramPage.diagramSpinner).toBeVisible();
    });

    await test.step('Deploy new process and verify it loads', async () => {
      await deploy(['./resources/newProcess.bpmn']);
      await sleep(2000);

      await expect(operateDiagramPage.diagram).toBeInViewport({timeout: 20000});

      await expect(operateDiagramPage.diagramSpinner).toBeHidden();

      await expect(
        operateProcessesPage.noMatchingInstancesMessage,
      ).toBeVisible();

      await expect(operateFiltersPanelPage.processNameFilter).toBeEnabled();
      await expect
        .poll(() => operateFiltersPanelPage.processNameFilter.inputValue())
        .toBe('Test Process');
    });
  });

  test('Cancel process instance', async ({
    operateProcessesPage,
    operateFiltersPanelPage,
    page,
  }) => {
    await test.step('Navigate to process to cancel', async () => {
      await operateFiltersPanelPage.displayOptionalFilter(
        'Process Instance Key(s)',
      );
      await operateFiltersPanelPage.processInstanceKeysFilter.fill(
        instanceToCancel.processInstanceKey,
      );

      await operateFiltersPanelPage.selectProcess(
        'Delete Process Definition API Test',
      );
      await operateFiltersPanelPage.selectVersion('1');

      await expect(page.getByText('1 result')).toBeVisible({timeout: 60000});
    });

    await test.step('Cancel the running instance', async () => {
      await operateProcessesPage.cancelProcessInstanceButton.click();
      await operateProcessesPage.cancelProcessInstanceDialogButton.click();

      await expect(
        operateProcessesPage.noMatchingInstancesMessage,
      ).toBeVisible();
    });
  });
});
