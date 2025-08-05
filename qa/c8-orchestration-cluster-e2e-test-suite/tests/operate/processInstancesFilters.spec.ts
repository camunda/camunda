/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createInstances, createSingleInstance} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {waitForAssertion} from 'utils/waitForAssertion';

type ProcessInstance = {processInstanceKey: number};

let callActivityProcessInstance: ProcessInstance;
let orderProcessInstance: ProcessInstance;

test.beforeAll(async ({request}) => {
  await deploy([
    './resources/processWithMultipleVersions_v_1.bpmn',
    './resources/processWithAnError.bpmn',
  ]);

  await createInstances('processWithMultipleVersions', 1, 1);
  await createInstances('processWithAnError', 1, 1);

  await deploy(['./resources/processWithMultipleVersions_v_2.bpmn']);
  await createInstances('processWithMultipleVersions', 2, 1);

  await deploy(['./resources/orderProcess_v_1.bpmn']);
  orderProcessInstance = {
    processInstanceKey: Number(
      (
        await createSingleInstance('orderProcess', 1, {
          filtersTest: 123,
        })
      ).processInstanceKey,
    ),
  };

  await deploy([
    './resources/callActivityProcess.bpmn',
    './resources/calledProcess.bpmn',
  ]);

  callActivityProcessInstance = {
    processInstanceKey: Number(
      (await createSingleInstance('CallActivityProcess', 1, {filtersTest: 456}))
        .processInstanceKey,
    ),
  };
});

test.describe('Process Instances Filters', () => {
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
  test('Filter process instances by parent key, date range, and error message', async ({
    page,
    operateProcessesPage,
    operateFiltersPanelPage,
  }) => {
    await test.step('Filter by Parent Process Instance Key and assert results', async () => {
      const callActivityProcessInstanceKey =
        callActivityProcessInstance.processInstanceKey.toString();

      await operateFiltersPanelPage.displayOptionalFilter(
        'Parent Process Instance Key',
      );
      await operateFiltersPanelPage.fillParentProcessInstanceKeyFilter(
        callActivityProcessInstanceKey,
      );
      await expect(
        operateProcessesPage.noMatchingInstancesMessage,
      ).toBeVisible();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('1 result')).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await expect(operateProcessesPage.parentInstanceIdCell).toHaveText(
        callActivityProcessInstanceKey,
      );
      expect(await operateProcessesPage.processInstancesTable.count()).toBe(1);
    });

    await test.step('Reset filter', async () => {
      await operateFiltersPanelPage.clickResetFilters();
      await expect(
        operateFiltersPanelPage.parentProcessInstanceKey,
      ).toBeHidden();
      await expect
        .poll(() => operateProcessesPage.processInstancesTable.count())
        .toBeGreaterThan(1);
    });

    await test.step('Filter by End Date Range and assert results', async () => {
      await operateProcessesPage.clickProcessCompletedCheckbox();

      let currentRowCount =
        await operateProcessesPage.processInstancesTable.count();
      const endDate = await operateProcessesPage.endDateCell.innerText();
      const day =
        endDate === '--' ? new Date().getDate() : new Date(endDate).getDate();

      await operateFiltersPanelPage.displayOptionalFilter('End Date Range');
      await operateFiltersPanelPage.pickDateTimeRange({
        fromDay: '1',
        toDay: `${day}`,
      });
      await operateFiltersPanelPage.clickApply();
      await expect
        .poll(() => operateProcessesPage.processInstancesTable.count())
        .toBeLessThan(currentRowCount);

      currentRowCount =
        await operateProcessesPage.processInstancesTable.count();
      await operateFiltersPanelPage.clickResetFilters();
      await expect
        .poll(() => operateProcessesPage.processInstancesTable.count())
        .toBeGreaterThan(currentRowCount);
    });

    await test.step('Filter by Error Message and assert results', async () => {
      let currentRowCount =
        await operateProcessesPage.processInstancesTable.count();
      await operateFiltersPanelPage.displayOptionalFilter('Error Message');
      await operateFiltersPanelPage.fillErrorMessageFilter(
        "failed to evaluate expression 'nonExistingClientId': no variable found for name 'nonExistingClientId'",
      );
      await expect
        .poll(() => operateProcessesPage.processInstancesTable.count())
        .toBeLessThan(currentRowCount);
    });

    await test.step('Filter by Start Date Range and assert results', async () => {
      await operateFiltersPanelPage.displayOptionalFilter('Start Date Range');
      await operateFiltersPanelPage.pickDateTimeRange({
        fromDay: '1',
        toDay: '1',
        fromTime: '00:00:00',
        toTime: '00:00:00',
      });
      await operateFiltersPanelPage.clickApply();
      await expect(
        operateProcessesPage.noMatchingInstancesMessage,
      ).toBeVisible();

      await operateFiltersPanelPage.clickResetFilters();
      await expect(
        operateProcessesPage.noMatchingInstancesMessage,
      ).toBeHidden();

      await expect(operateFiltersPanelPage.errorMessageFilter).toBeHidden();
      await expect(operateFiltersPanelPage.startDateFilter).toBeHidden();
    });
  });

  test('Interaction between diagram and filters', async ({
    operateProcessesPage,
    operateFiltersPanelPage,
  }) => {
    await test.step('Filter by Process Name and assert version value', async () => {
      await operateFiltersPanelPage.selectProcess(
        'Process With Multiple Versions',
      );
      await expect
        .poll(() => operateFiltersPanelPage.processVersionFilter.innerText())
        .toBe('2');
    });

    await test.step('Change version and see flow node filter has been reset', async () => {
      await operateFiltersPanelPage.selectVersion('1');
      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue('');

      await operateFiltersPanelPage.selectFlowNode('StartEvent_1');
      await expect(
        operateProcessesPage.noMatchingInstancesMessage,
      ).toBeVisible();
    });

    await test.step('Select another flow node from the diagram', async () => {
      await operateProcessesPage.diagram.clickFlowNode('always fails');

      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue(
        'Always fails',
      );
    });
    await test.step('Select same flow node again and see filter is removed', async () => {
      await operateProcessesPage.diagram.clickFlowNode('always fails');

      await expect(
        operateProcessesPage.noMatchingInstancesMessage,
      ).toBeHidden();

      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue('');
    });
  });

  test('Variable filtering with single and multiple values', async ({
    page,
    operateProcessesPage,
    operateFiltersPanelPage,
  }) => {
    test.slow();
    const callActivityProcessInstanceKey =
      callActivityProcessInstance.processInstanceKey.toString();
    const orderProcessInstanceKey =
      orderProcessInstance.processInstanceKey.toString();

    await test.step('Filter by Process Instance Keys including completed instances', async () => {
      await operateFiltersPanelPage.displayOptionalFilter(
        'Process Instance Key(s)',
      );
      await operateFiltersPanelPage.processInstanceKeysFilter.fill(
        `${orderProcessInstanceKey}, ${callActivityProcessInstanceKey}`,
      );
      await operateProcessesPage.clickProcessCompletedCheckbox();
    });
    await test.step('Add Variable Filter', async () => {
      await operateFiltersPanelPage.displayOptionalFilter('Variable');
      await operateFiltersPanelPage.fillVariableNameFilter('filtersTest');
      await operateFiltersPanelPage.fillVariableValueFilter('123');
    });

    await test.step('Open json editor modal and check content', async () => {
      await operateFiltersPanelPage.clickJsonEditorModal();
      await expect(
        operateFiltersPanelPage.dialogEditVariableValueText,
      ).toBeVisible();
      await expect(
        operateFiltersPanelPage.variableEditorDialog.getByText('123'),
      ).toBeVisible();
    });

    await test.step('Close modal', async () => {
      await operateFiltersPanelPage.closeModalWithCancel();
      await expect(operateFiltersPanelPage.variableEditorDialog).toBeHidden();
    });

    await test.step('Check that process instances table is filtered correctly', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('1 result')).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await operateFiltersPanelPage.displayOptionalFilter('Variable');
          await operateFiltersPanelPage.fillVariableNameFilter('filtersTest');
          await operateFiltersPanelPage.fillVariableValueFilter('123');
        },
      });
      await expect(
        operateProcessesPage.processInstancesTable.getByText(
          orderProcessInstanceKey.toString(),
          {
            exact: true,
          },
        ),
      ).toBeVisible({timeout: 60000});
      await expect(
        operateProcessesPage.processInstancesTable.getByText(
          callActivityProcessInstanceKey.toString(),
          {exact: true},
        ),
      ).toBeHidden();
    });

    await test.step('Switch to multiple mode and add multiple variables', async () => {
      await operateFiltersPanelPage.clickMultipleVariablesSwitch();
      await operateFiltersPanelPage.variableNameFilter.fill('filtersTest');
      await operateFiltersPanelPage.variableValueFilter.fill('123, 456');
    });

    await test.step('Open editor modal and check content', async () => {
      await operateFiltersPanelPage.clickJsonEditorModal();
      await expect(
        operateFiltersPanelPage.dialogEditMultipleVariableValueText,
      ).toBeVisible();
      await expect(
        operateFiltersPanelPage.variableEditorDialog.getByText('123, 456'),
      ).toBeVisible();
    });

    await test.step('Close modal', async () => {
      await operateFiltersPanelPage.closeModalWithCancel();
      await expect(operateFiltersPanelPage.variableEditorDialog).toBeHidden();
    });

    await test.step('Check that process instances table is filtered correctly', async () => {
      await expect(page.getByText('2 results')).toBeVisible({timeout: 60000});
      await expect(
        operateProcessesPage.processInstancesTable.getByText(
          orderProcessInstanceKey.toString(),
          {
            exact: true,
          },
        ),
      ).toBeVisible();
      await expect(
        operateProcessesPage.processInstancesTable.getByText(
          callActivityProcessInstanceKey.toString(),
          {exact: true},
        ),
      ).toBeVisible();
    });
  });
});
