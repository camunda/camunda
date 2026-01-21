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
import {sleep} from 'utils/sleep';
import {getNewOperationIds} from 'utils/getNewOperationIds';

type ProcessInstance = {processInstanceKey: number};

let callActivityProcessInstance: ProcessInstance;
let orderProcessInstance: ProcessInstance;
let variableProcessInstance: ProcessInstance;

test.beforeAll(async () => {
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

  await deploy(['./resources/Variable_Process.bpmn']);
  variableProcessInstance = {
    processInstanceKey: Number(
      (await createSingleInstance('Variable_Process', 1, {filtersTest: 604}))
        .processInstanceKey,
    ),
  };

  await deploy(['./resources/Versioned Process.bpmn']);
  await deploy(['./resources/Versioned Process_2.bpmn']);
  await deploy(['./resources/ProcessToCancel.bpmn']);
  await deploy(['./resources/NamedEventsProcess.bpmn']);
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
  test('Filter process instances by parent key, date range, variable, instance key and error message', async ({
    page,
    operateProcessesPage,
    operateFiltersPanelPage,
    operateOperationPanelPage,
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
      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('1 result')).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await operateFiltersPanelPage.clickCompletedInstancesCheckbox();
      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('2 result')).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await expect(operateProcessesPage.parentInstanceIdCell).toHaveText(
        callActivityProcessInstanceKey,
      );
      expect(await operateProcessesPage.processInstancesTable.count()).toBe(2);
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

    await test.step('Filter by Parent Instance Key and by Error Message and assert results', async () => {
      const callActivityProcessInstanceKey =
        callActivityProcessInstance.processInstanceKey.toString();

      await operateFiltersPanelPage.displayOptionalFilter(
        'Parent Process Instance Key',
      );
      await operateFiltersPanelPage.fillParentProcessInstanceKeyFilter(
        callActivityProcessInstanceKey,
      );
      await expect(page.getByText('1 result')).toBeVisible();
      await operateFiltersPanelPage.clickCompletedInstancesCheckbox();
      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('2 result')).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      const errorMessage =
        "Failed to extract the correlation key for 'nonExistingClientId'";
      await operateFiltersPanelPage.displayOptionalFilter('Error Message');
      await operateFiltersPanelPage.fillErrorMessageFilter(errorMessage);

      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('1 result')).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    const day = new Date().getDate();

    await test.step('Filter by End Date Range and assert results', async () => {
      await operateFiltersPanelPage.clickCompletedInstancesCheckbox();

      let currentRowCount =
        await operateProcessesPage.processInstancesTable.count();

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

    await test.step('Filter by variable and assert results', async () => {
      await operateFiltersPanelPage.displayOptionalFilter('Variable');
      await operateFiltersPanelPage.fillVariableNameFilter('filtersTest');
      await operateFiltersPanelPage.fillVariableValueFilter('604');

      const variableProcessInstanceKey =
        variableProcessInstance.processInstanceKey.toString();

      await sleep(1_000);

      await waitForAssertion({
        assertion: async () => {
          const variableProcessInstanceKeys = new Set<string>();
          for (const element of await operateProcessesPage.processInstancesTable
            .getByTestId('cell-processInstanceKey')
            .elementHandles()) {
            variableProcessInstanceKeys.add(await element.innerText());
          }
          expect(
            variableProcessInstanceKeys.has(variableProcessInstanceKey),
          ).toBeTruthy();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Filter by process instance key with one key and assert results', async () => {
      await operateFiltersPanelPage.clickResetFilters();
      const variableProcessInstanceKey =
        variableProcessInstance.processInstanceKey.toString();

      await operateFiltersPanelPage.displayOptionalFilter(
        'Process Instance Key(s)',
      );

      await operateFiltersPanelPage.fillProcessInstanceKeyFilter(
        variableProcessInstanceKey,
      );
      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessesPage.processInstanceKeyCell).toHaveText(
            variableProcessInstanceKey,
          );
          await expect(page.getByText('1 result')).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Filter by process instance key with multiple keys and assert results', async () => {
      const variableProcessInstanceKey =
        variableProcessInstance.processInstanceKey.toString();
      const callActivityProcessInstanceKey =
        callActivityProcessInstance.processInstanceKey.toString();
      await operateFiltersPanelPage.resetFiltersButton.click();
      await operateFiltersPanelPage.displayOptionalFilter(
        'Process Instance Key(s)',
      );

      await expect(
        operateFiltersPanelPage.finishedInstancesCheckbox,
      ).toBeVisible();
      await expect(
        operateFiltersPanelPage.finishedInstancesCheckbox,
      ).toBeEnabled();
      await operateFiltersPanelPage.clickFinishedInstancesCheckbox();

      await operateFiltersPanelPage.fillProcessInstanceKeyFilter(
        `${variableProcessInstanceKey}, ${callActivityProcessInstanceKey}`,
      );

      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('2 results')).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Reset filter', async () => {
      await operateFiltersPanelPage.clickResetFilters();
      await expect(
        operateFiltersPanelPage.processInstanceKeysFilter,
      ).toBeHidden();
      await expect
        .poll(() => operateProcessesPage.processInstancesTable.count())
        .toBeGreaterThan(1);
    });

    await test.step('Filter by variable and operation id and assert results', async () => {
      const processToCancelMeowInstance = {
        processInstanceKey: Number(
          (await createSingleInstance('ProcessToCancel', 1, {sound: 'meow'}))
            .processInstanceKey,
        ),
      };

      const processToCancelGawInstance = {
        processInstanceKey: Number(
          (await createSingleInstance('ProcessToCancel', 1, {sound: 'gaw'}))
            .processInstanceKey,
        ),
      };

      const processToCancelInstanceMeowIK =
        processToCancelMeowInstance.processInstanceKey.toString();
      const processToCancelInstanceGawIK =
        processToCancelGawInstance.processInstanceKey.toString();

      await operateFiltersPanelPage.displayOptionalFilter(
        'Process Instance Key(s)',
      );
      await operateFiltersPanelPage.fillProcessInstanceKeyFilter(
        `${processToCancelInstanceMeowIK}, ${processToCancelInstanceGawIK}`,
      );

      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('2 results')).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      operateOperationPanelPage.beforeOperationOperationPanelEntries =
        await operateOperationPanelPage.operationIdsEntries();

      await operateOperationPanelPage.collapseOperationIdField();

      await expect(operateProcessesPage.tableLoadingSpinner).toBeHidden({
        timeout: 30000,
      });

      await expect(
        page
          .getByTestId('cell-processInstanceKey')
          .getByText(processToCancelInstanceMeowIK),
      ).toBeVisible({timeout: 30000});

      await operateProcessesPage.selectProcessCheckboxByPIK(
        processToCancelInstanceMeowIK,
        processToCancelInstanceGawIK,
      );

      await operateProcessesPage.clickCancelBatchOperationButton();

      await operateProcessesPage.clickCancelProcessInstanceDialogButton();

      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessesPage.noMatchingInstancesMessage,
          ).toBeVisible({timeout: 30000});
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      operateOperationPanelPage.afterOperationOperationPanelEntries =
        await operateOperationPanelPage.operationIdsEntries();

      let newIds = getNewOperationIds(
        operateOperationPanelPage.beforeOperationOperationPanelEntries,
        operateOperationPanelPage.afterOperationOperationPanelEntries,
        'Cancel',
      );

      await waitForAssertion({
        assertion: async () => {
          expect(newIds.length).toBeGreaterThan(0);
        },
        onFailure: async () => {
          await sleep(1_000);
          operateOperationPanelPage.afterOperationOperationPanelEntries =
            await operateOperationPanelPage.operationIdsEntries();
          newIds = getNewOperationIds(
            operateOperationPanelPage.beforeOperationOperationPanelEntries,
            operateOperationPanelPage.afterOperationOperationPanelEntries,
            'Cancel',
          );
        },
        maxRetries: 60,
      });

      const operationId = newIds[0];

      await operateOperationPanelPage.collapseOperationIdField();

      await operateFiltersPanelPage.clickResetFilters();
      await operateFiltersPanelPage.clickRunningInstancesCheckbox();
      await operateFiltersPanelPage.clickFinishedInstancesCheckbox();

      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessesPage.processInstancesTable.first(),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await operateFiltersPanelPage.displayOptionalFilter('Operation Id');
      await operateFiltersPanelPage.fillOperationIdFilter(operationId);

      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('2 results')).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await operateFiltersPanelPage.displayOptionalFilter('Variable');
      await operateFiltersPanelPage.fillVariableNameFilter('sound');
      await operateFiltersPanelPage.fillVariableValueFilter('"meow"');

      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('1 results')).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Filter by end and start date range', async () => {
      await operateFiltersPanelPage.resetFiltersButton.click();

      await operateFiltersPanelPage.displayOptionalFilter('Start Date Range');
      await operateFiltersPanelPage.pickDateTimeRange({
        fromDay: '1',
        toDay: '1',
        fromTime: '00:00:00',
        toTime: '00:00:00',
      });
      await operateFiltersPanelPage.clickApply();

      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessesPage.noMatchingInstancesMessage,
          ).toBeVisible({timeout: 30000});
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await operateFiltersPanelPage.displayOptionalFilter('End Date Range');

      const todayDate = new Date();
      console.log('Today date:', todayDate.getDate());

      await operateFiltersPanelPage.pickDateTimeRange({
        fromDay: todayDate.getDate().toString(),
        toDay: todayDate.getDate().toString(),
        fromTime: '00:00:00',
        toTime: '23:59:59',
      });
      await operateFiltersPanelPage.clickApply();

      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessesPage.noMatchingInstancesMessage,
          ).toBeVisible({timeout: 30000});
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await operateFiltersPanelPage.removeOptionalFilter('Start Date Range');

      await expect(operateFiltersPanelPage.startDateFilter).toBeHidden();
      await operateFiltersPanelPage.displayOptionalFilter('Start Date Range');
      await expect(operateFiltersPanelPage.startDateFilter).toBeVisible();

      await operateFiltersPanelPage.pickDateTimeRange({
        fromDay: todayDate.getDate().toString(),
        toDay: todayDate.getDate().toString(),
        fromTime: '00:00:00',
        toTime: '23:59:59',
      });

      await operateFiltersPanelPage.clickApply();

      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessesPage.noMatchingInstancesMessage,
          ).toBeVisible({timeout: 30000});
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await operateFiltersPanelPage.clickFinishedInstancesCheckbox();
      await expect
        .poll(() => operateProcessesPage.processInstancesTable.count())
        .toBeGreaterThanOrEqual(2);
    });
  });

  test('Interaction between diagram and filters', async ({
    operateProcessesPage,
    operateFiltersPanelPage,
    page,
  }) => {
    await test.step('Filter by Process Name and assert version value', async () => {
      await operateFiltersPanelPage.selectProcess(
        'Process With Multiple Versions',
      );
      await operateFiltersPanelPage.selectVersion('2');
      await waitForAssertion({
        assertion: async () => {
          await expect
            .poll(() =>
              operateFiltersPanelPage.processVersionFilter.innerText(),
            )
            .toBe('2');
        },
        onFailure: async () => {
          await page.reload();
          await operateFiltersPanelPage.selectProcess(
            'Process With Multiple Versions',
          );
          await operateFiltersPanelPage.selectVersion('2');
        },
      });
    });

    await test.step('Change version and see flow node filter has been reset', async () => {
      await operateFiltersPanelPage.selectVersion('1');
      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue('');

      await operateFiltersPanelPage.selectFlowNode('StartEvent_1');
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessesPage.noMatchingInstancesMessage,
          ).toBeVisible({timeout: 60000});
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Select another flow node from the diagram', async () => {
      await operateProcessesPage.diagram.clickFlowNode('alwaysFails');

      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue(
        'Always fails',
      );
    });
    await test.step('Select same flow node again and see filter is removed', async () => {
      await operateProcessesPage.diagram.clickFlowNode('alwaysFails');

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
      await operateFiltersPanelPage.clickCompletedInstancesCheckbox();
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

  test('Verify flow node selection reflects in filters and assert results', async ({
    operateFiltersPanelPage,
    operateDiagramPage,
    page,
  }) => {
    await test.step('Select process from dropdown and assert results', async () => {
      await operateFiltersPanelPage.selectProcess('NamedEventsProcess');
      await waitForAssertion({
        assertion: async () => {
          await expect(operateFiltersPanelPage.processNameFilter).toHaveValue(
            'NamedEventsProcess',
          );
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await expect(operateDiagramPage.diagram).toBeVisible();
    });

    await test.step('Click start flow node and assert filter value and results', async () => {
      await operateDiagramPage.clickFlowNode('StartEvent_OrderReceived');
      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue(
        'Order received',
      );
    });

    await test.step('Click task flow nodes and assert filter value and results', async () => {
      await operateDiagramPage.clickFlowNode('Activity_SendOrderConfirmation');
      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue(
        'Send order confirmation',
      );

      await operateDiagramPage.clickFlowNode('Activity_PackItems');
      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue(
        'Pack Items',
      );

      await operateDiagramPage.clickFlowNode('Activity_SendItems');
      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue(
        'Send items',
      );
    });

    await test.step('Click end event flow node and assert filter value and results', async () => {
      await operateDiagramPage.clickFlowNode('Event_OrderFulfilled');
      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue(
        'Order fulfilled',
      );
    });

    await test.step('Click intermediate throw event flow node and assert results', async () => {
      await operateDiagramPage.clickFlowNode('Event_OrderConfirmed');
      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue(
        'Order Confirmed',
      );
    });

    await test.step('Click same flow node again and see filter is removed', async () => {
      await operateDiagramPage.clickFlowNode('Event_OrderConfirmed');

      await expect(operateFiltersPanelPage.flowNodeFilter).toHaveValue('');
    });
  });

  test('Reset filters when navigating away and assert results', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateHomePage,
  }) => {
    await test.step('Apply Process Instance Key and variable filter and assert results', async () => {
      await operateFiltersPanelPage.displayOptionalFilter(
        'Process Instance Key(s)',
      );
      const variableProcessInstanceKey =
        variableProcessInstance.processInstanceKey.toString();
      await operateFiltersPanelPage.fillProcessInstanceKeyFilter(
        variableProcessInstanceKey,
      );

      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessesPage.processInstanceKeyCell).toHaveText(
            variableProcessInstanceKey,
          );
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await operateFiltersPanelPage.displayOptionalFilter('Variable');
      await operateFiltersPanelPage.fillVariableNameFilter('filtersTest');
      await operateFiltersPanelPage.fillVariableValueFilter('604');

      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('1 result')).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Click on the "Process" link button in the header and assert the results', async () => {
      await operateHomePage.clickProcessesTab();
      await expect(
        operateFiltersPanelPage.processInstanceKeysFilter,
      ).toBeHidden();
      await expect(operateFiltersPanelPage.variableNameFilter).toBeHidden();
      await operateFiltersPanelPage.validateCheckedState(
        [
          operateFiltersPanelPage.activeInstancesCheckbox,
          operateFiltersPanelPage.incidentsInstancesCheckbox,
          operateFiltersPanelPage.runningInstancesCheckbox,
        ],

        [
          operateFiltersPanelPage.finishedInstancesCheckbox,
          operateFiltersPanelPage.completedInstancesCheckbox,
          operateFiltersPanelPage.canceledInstancesCheckbox,
        ],
      );
    });
  });
});
