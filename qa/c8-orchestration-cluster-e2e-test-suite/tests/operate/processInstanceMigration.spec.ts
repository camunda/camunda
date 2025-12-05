/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createInstances, publishMessage} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp, validateURL} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';
import {waitForAssertion} from 'utils/waitForAssertion';
import {getNewOperationIds} from 'utils/getNewOperationIds';

const PROCESS_INSTANCE_COUNT = 10;
const AUTO_MIGRATION_INSTANCE_COUNT = 6;
const MANUAL_MIGRATION_INSTANCE_COUNT = 3;

let migratedIds: string[];

type ProcessDeployment = {
  readonly bpmnProcessId: string;
  readonly version: number;
};

type TestProcesses = {
  readonly processV1: ProcessDeployment;
  readonly processV2: ProcessDeployment;
  readonly processV3: ProcessDeployment;
};

let testProcesses: TestProcesses;

test.beforeAll(async () => {
  await deploy(['./resources/orderProcessMigration_v_1.bpmn']);
  const processV1: ProcessDeployment = {
    bpmnProcessId: 'orderProcessMigration',
    version: 1,
  };

  await Promise.all(
    [...new Array(PROCESS_INSTANCE_COUNT)].map((_, index) =>
      createInstances(processV1.bpmnProcessId, processV1.version, 1, {
        key1: 'myFirstCorrelationKey',
        key2: 'mySecondCorrelationKey',
        key3: `myCorrelationKey${index}`,
      }),
    ),
  );

  await Promise.all(
    [...new Array(PROCESS_INSTANCE_COUNT)].map((_, index) =>
      publishMessage('Message_4', `myCorrelationKey${index}`),
    ),
  );

  await sleep(2000);

  await deploy(['./resources/orderProcessMigration_v_2.bpmn']);
  const processV2: ProcessDeployment = {
    bpmnProcessId: 'orderProcessMigration',
    version: 2,
  };

  await deploy(['./resources/orderProcessMigration_v_3.bpmn']);
  const processV3: ProcessDeployment = {
    bpmnProcessId: 'newOrderProcessMigration',
    version: 1,
  };

  testProcesses = {
    processV1,
    processV2,
    processV3,
  };
  await sleep(3000);
});

test.describe.serial('Process Instance Migration', () => {
  test.slow();
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

  test('Auto mapping migration', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateProcessMigrationModePage,
    operateOperationPanelPage,
  }) => {
    const sourceVersion = testProcesses.processV1.version.toString();
    const sourceBpmnProcessId = testProcesses.processV1.bpmnProcessId;
    const targetVersion = testProcesses.processV2.version.toString();
    const targetBpmnProcessId = testProcesses.processV2.bpmnProcessId;

    await test.step('Filter by process name and version', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessesPage.resultsText.first()).toBeVisible({
            timeout: 30000,
          });
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 5,
      });
      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(sourceVersion);

      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('10 results')).toBeVisible({
            timeout: 30000,
          });
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Select first 6 process instances for migration', async () => {
      await operateProcessesPage.selectProcessInstances(
        AUTO_MIGRATION_INSTANCE_COUNT,
      );
      operateOperationPanelPage.beforeOperationOperationPanelEntries =
        await operateOperationPanelPage.operationIdsEntries();
      await operateProcessesPage.startMigration();
      await sleep(1000);
    });

    await test.step('Verify target process is preselected with auto-mapping and Complete Migration', async () => {
      await expect(
        operateProcessMigrationModePage.targetProcessCombobox,
      ).toHaveValue(targetBpmnProcessId);

      await operateProcessMigrationModePage.verifyFlowNodeMappings([
        {
          label: 'Target flow node for Check payment',
          targetValue: 'checkPayment',
        },
        {
          label: 'Target flow node for Ship Articles',
          targetValue: 'shipArticles',
        },
        {
          label: 'Target flow node for Request for payment',
          targetValue: 'requestForPayment',
        },
        {label: 'Target flow node for Task A', targetValue: 'TaskA'},
        {label: 'Target flow node for Task B', targetValue: 'TaskB'},
        {label: 'Target flow node for Task C', targetValue: 'TaskC'},
        {label: 'Target flow node for Task D', targetValue: 'TaskD'},
        {
          label: 'Target flow node for message interrupting',
          targetValue: 'MessageInterrupting',
        },
        {
          label: 'Target flow node for timer interrupting',
          targetValue: 'TimerInterrupting',
        },
        {
          label: 'Target flow node for message non-interrupting',
          targetValue: 'MessageNonInterrupting',
        },
        {
          label: 'Target flow node for timer non-interrupting',
          targetValue: 'TimerNonInterrupting',
        },
        {
          label: /Target flow node for message intermediate catch$/i,
          targetValue: 'MessageIntermediateCatch',
        },
        {
          label: /Target flow node for timer intermediate catch$/i,
          targetValue: 'TimerIntermediateCatch',
        },
        {
          label: 'Target flow node for message event sub process',
          targetValue: 'MessageEventSubProcess',
        },
        {
          label: 'Target flow node for timer event sub process',
          targetValue: 'TimerEventSubProcess',
        },
        {label: 'Target flow node for Task E', targetValue: 'TaskE'},
        {label: 'Target flow node for Task F', targetValue: 'TaskF'},
        {
          label: 'Target flow node for message receive task',
          targetValue: 'MessageReceiveTask',
        },
        {
          label: 'Target flow node for business rule task',
          targetValue: 'BusinessRuleTask',
        },
        {label: 'Target flow node for Script Task', targetValue: 'ScriptTask'},
        {label: 'Target flow node for Send Task', targetValue: 'SendTask'},
        {
          label: 'Target flow node for timer start event',
          targetValue: 'TimerStartEvent',
        },
        {
          label: 'Target flow node for signal start event',
          targetValue: 'SignalStartEvent',
        },
        {
          label: 'Target flow node for signal boundary event',
          targetValue: 'SignalBoundaryEvent',
        },
        {
          label: 'Target flow node for signal intermediate catch',
          targetValue: 'SignalIntermediateCatch',
        },
        {
          label: 'Target flow node for signal event sub process',
          targetValue: 'SignalEventSubProcess',
        },
        {
          label: 'Target flow node for error event sub process',
          targetValue: 'ErrorEventSubProcess',
        },
        {
          label: 'Target flow node for error start event',
          targetValue: 'ErrorStartEvent',
        },
        {label: 'Target flow node for Task G', targetValue: 'TaskG'},
        {label: 'Target flow node for sub process', targetValue: 'SubProcess'},
        {
          label: 'Target flow node for multi instance sub process',
          targetValue: 'MultiInstanceSubProcess',
        },
        {
          label: 'Target flow node for multi instance task',
          targetValue: 'MultiInstanceTask',
        },
        {
          label: 'Target flow node for message start event',
          targetValue: 'MessageStartEvent',
        },
      ]);

      await operateProcessMigrationModePage.migrateProcess(targetVersion);
    });

    await test.step('Verify migration operation is created and completes', async () => {
      await expect(operateProcessesPage.operationsList).toBeVisible({
        timeout: 30000,
      });

      await operateProcessesPage.waitForOperationToComplete();
      operateOperationPanelPage.afterOperationOperationPanelEntries =
        await operateOperationPanelPage.operationIdsEntries();
    });

    await test.step('Verify 6 instances migrated to target version', async () => {
      await expect(operateProcessesPage.operationSuccessMessage).toBeVisible({
        timeout: 120000,
      });

      migratedIds = getNewOperationIds(
        operateOperationPanelPage.beforeOperationOperationPanelEntries,
        operateOperationPanelPage.afterOperationOperationPanelEntries,
        'Migrate',
      );

      await waitForAssertion({
        assertion: async () => {
          expect(migratedIds.length).toBeGreaterThan(0);
        },
        onFailure: async () => {
          await page.waitForTimeout(1000);
          operateOperationPanelPage.afterOperationOperationPanelEntries =
            await operateOperationPanelPage.operationIdsEntries();
          migratedIds = getNewOperationIds(
            operateOperationPanelPage.beforeOperationOperationPanelEntries,
            operateOperationPanelPage.afterOperationOperationPanelEntries,
            'Migrate',
          );
        },
        maxRetries: 30,
      });

      await operateOperationPanelPage.clickOperationEntryById(migratedIds[0]);

      await validateURL(page, /operationId=/);
      await expect(page.getByText('6 results')).toBeVisible({
        timeout: 30000,
      });

      await expect(operateProcessesPage.getVersionCells('2')).toHaveCount(6, {
        timeout: 30000,
      });
    });
  });

  test('Migrated event sub processes', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateOperationPanelPage,
  }) => {
    const targetBpmnProcessId = testProcesses.processV2.bpmnProcessId;
    const targetVersion = testProcesses.processV2.version.toString();

    await test.step('Navigate to processes and expand operations panel', async () => {
      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessesPage.resultsText.first()).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 4,
      });

      await operateOperationPanelPage.expandOperationIdField();
    });

    await test.step('Get migration operation ID and verify TaskF instances', async () => {
      const operationId = migratedIds[0];

      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      const baseUrl = process.env.CORE_APPLICATION_OPERATE_URL;
      const url = `${baseUrl}/operate/processes?active=true&incidents=true&process=${targetBpmnProcessId}&version=${targetVersion}&operationId=${operationId}&flowNodeId=TaskF`;
      console.log('Navigating to URL:', url);
      await page.goto(url);
      // await page.goto(
      //   `operate/processes?active=true&incidents=true&process=${targetBpmnProcessId}&version=${targetVersion}&operationId=${operationId}&flowNodeId=TaskF`,
      // );

      await expect(page.getByText('6 results')).toBeVisible({timeout: 30000});
    });
  });

  test('Manual mapping migration', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateProcessMigrationModePage,
    operateOperationPanelPage,
  }) => {
    test.slow();
    const sourceVersion = testProcesses.processV2.version.toString();
    const sourceBpmnProcessId = testProcesses.processV2.bpmnProcessId;
    const targetVersion = testProcesses.processV3.version.toString();
    const targetBpmnProcessId = testProcesses.processV3.bpmnProcessId;

    await test.step('Filter by process name and version', async () => {
      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(sourceVersion);

      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessesPage.resultsText.first()).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 4,
      });
    });

    await test.step('Select 3 process instances for migration', async () => {
      await operateProcessesPage.selectProcessInstances(
        MANUAL_MIGRATION_INSTANCE_COUNT,
      );
      operateOperationPanelPage.beforeOperationOperationPanelEntries =
        await operateOperationPanelPage.operationIdsEntries();
      await operateProcessesPage.startMigration();
    });

    await test.step('Manually select target process and version', async () => {
      await operateProcessMigrationModePage.targetProcessCombobox.click();
      await operateProcessMigrationModePage
        .getOptionByName(targetBpmnProcessId)
        .click();

      await operateProcessMigrationModePage.targetVersionDropdown.click();
      await operateProcessMigrationModePage
        .getOptionByName(targetVersion)
        .click();
    });

    await test.step('Manually map flow nodes', async () => {
      await operateProcessMigrationModePage.mapFlowNode(
        'Check payment',
        'Ship Articles 2',
      );

      await operateProcessMigrationModePage.mapFlowNode('Task A', 'Task C2');
      await operateProcessMigrationModePage.mapFlowNode('Task B', 'Task B2');
      await operateProcessMigrationModePage.mapFlowNode('Task C', 'Task A2');
      await operateProcessMigrationModePage.mapFlowNode('Task D', 'Task D2');

      await operateProcessMigrationModePage.mapFlowNode(
        'Message interrupting',
        'Message non-interrupting 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Timer interrupting',
        'Timer interrupting 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Message non-interrupting',
        'Message interrupting 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Timer non-interrupting',
        'Timer non-interrupting 2',
      );

      await operateProcessMigrationModePage.mapFlowNode(
        'Message intermediate catch',
        'Message intermediate catch 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Timer intermediate catch',
        'Timer intermediate catch 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Message intermediate catch B',
        'Message intermediate catch B2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Timer intermediate catch B',
        'Timer intermediate catch B2',
      );

      await operateProcessMigrationModePage.mapFlowNode(
        'Message event sub process',
        'Message event sub process 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Timer event sub process',
        'Timer event sub process 2',
      );
      await operateProcessMigrationModePage.mapFlowNode('Task E', 'Task E2');
      await operateProcessMigrationModePage.mapFlowNode('Task F', 'Task F2');
      await operateProcessMigrationModePage.mapFlowNode(
        'Timer start event',
        'Timer start event 2',
      );

      await operateProcessMigrationModePage.mapFlowNode(
        'Signal intermediate catch',
        'Signal intermediate catch 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Signal start event',
        'Signal start event',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Signal boundary event',
        'Signal boundary event 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Signal event sub process',
        'Signal event sub process 2',
      );

      await operateProcessMigrationModePage.mapFlowNode(
        'Message receive task',
        'Message receive task 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Business rule task',
        'Business rule task 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Script task',
        'Script task 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Send Task',
        'Send Task 2',
      );

      await operateProcessMigrationModePage.mapFlowNode(
        'Event based gateway',
        'Event based gateway 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Exclusive gateway',
        'Exclusive gateway 2',
      );

      await operateProcessMigrationModePage.mapFlowNode(
        'Multi instance sub process',
        'Multi instance sub process 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Multi instance task',
        'Multi instance task 2',
      );
    });

    await test.step('Proceed to summary and verify migration details', async () => {
      await operateProcessMigrationModePage.clickNextButton();
      await expect(
        operateProcessMigrationModePage.summaryNotification,
      ).toContainText(
        `You are about to migrate 3 process instances from the process definition: ${sourceBpmnProcessId} - version ${sourceVersion} to the process definition: ${targetBpmnProcessId} - version ${targetVersion}`,
      );
    });

    await test.step('Confirm migration and type MIGRATE', async () => {
      await operateProcessMigrationModePage.clickConfirmButton();
      await operateProcessMigrationModePage.fillMigrationConfirmation(
        'MIGRATE',
      );
      await operateProcessMigrationModePage.clickMigrationConfirmationButton();
      await sleep(2000);
    });

    await test.step('Verify migration operation is created and completes', async () => {
      await operateProcessesPage.waitForOperationToComplete();
      operateOperationPanelPage.afterOperationOperationPanelEntries =
        await operateOperationPanelPage.operationIdsEntries();
    });

    await test.step('Verify 3 instances migrated to target version', async () => {
      await expect(operateProcessesPage.operationSuccessMessage).toBeVisible({
        timeout: 120000,
      });

      migratedIds = getNewOperationIds(
        operateOperationPanelPage.beforeOperationOperationPanelEntries,
        operateOperationPanelPage.afterOperationOperationPanelEntries,
        'Migrate',
      );

      await waitForAssertion({
        assertion: async () => {
          expect(migratedIds.length).toBeGreaterThan(0);
        },
        onFailure: async () => {
          await page.waitForTimeout(1000);
          operateOperationPanelPage.afterOperationOperationPanelEntries =
            await operateOperationPanelPage.operationIdsEntries();
          migratedIds = getNewOperationIds(
            operateOperationPanelPage.beforeOperationOperationPanelEntries,
            operateOperationPanelPage.afterOperationOperationPanelEntries,
            'Migrate',
          );
        },
        maxRetries: 60,
      });

      await operateOperationPanelPage.clickOperationEntryById(migratedIds[0]);

      await validateURL(page, /operationId=/);

      await expect(
        operateProcessesPage.getVersionCells(targetVersion),
      ).toHaveCount(3, {timeout: 60000});

      await expect(
        operateProcessesPage.getVersionCells(sourceVersion),
      ).toBeHidden();
    });

    await test.step('Verify remaining instances still at source version', async () => {
      await operateFiltersPanelPage.clickResetFilters();

      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(sourceVersion);
      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('3 results')).toBeVisible({
            timeout: 30000,
          });
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });
  });

  test('Migrated tasks', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateDiagramPage,
  }) => {
    const targetBpmnProcessId = testProcesses.processV3.bpmnProcessId;
    const targetVersion = testProcesses.processV3.version.toString();

    await test.step('Navigate to first migrated process instance', async () => {
      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessesPage.resultsText.first()).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 4,
      });

      await operateProcessesPage.clickProcessInstanceLink();
      await operateDiagramPage.resetDiagramZoomButton.click();
    });

    await test.step('Verify Send task migration', async () => {
      await operateDiagramPage.verifyFlowNodeMetadata('SendTask2', {
        hiddenText: 'expected worker failure',
      });
    });

    await test.step('Verify Task G migration', async () => {
      await operateDiagramPage.verifyFlowNodeMetadata('TaskG', {
        expectedText: 'endDate',
        hiddenText: '"endDate": "null"',
      });
    });

    await test.step('Verify Business rule task incident migration', async () => {
      await operateDiagramPage.clickFlowNode('BusinessRuleTask2');
      await operateDiagramPage.showMetaData();

      await operateDiagramPage.verifyIncidentInPopover(/invalid.*decision/i);

      await operateDiagramPage.closeMetadataModal();
    });
  });

  test('Migrated message events', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateDiagramPage,
  }) => {
    const targetBpmnProcessId = testProcesses.processV3.bpmnProcessId;
    const targetVersion = testProcesses.processV3.version.toString();

    await test.step('Navigate to first migrated process instance', async () => {
      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessesPage.resultsText.first()).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 4,
      });

      await operateProcessesPage.clickProcessInstanceLink();
      await operateDiagramPage.resetDiagramZoomButton.click();
    });

    await test.step('Verify Task A2 correlation key migration', async () => {
      await operateDiagramPage.verifyFlowNodeMetadata('TaskA2', {
        expectedText: [
          '"correlationKey": "mySecondCorrelationKey"',
          '"messageName": "Message_2",',
        ],
      });
    });

    await test.step('Verify Task C2 correlation key migration', async () => {
      await operateDiagramPage.verifyFlowNodeMetadata('TaskC2', {
        expectedText: [
          '"correlationKey": "myFirstCorrelationKey"',
          '"messageName": "Message_1",',
        ],
      });
    });

    await test.step('Verify Message receive task migration', async () => {
      await sleep(500);
      await operateDiagramPage.verifyFlowNodeMetadata('MessageReceiveTask2', {
        expectedText: [
          '"correlationKey": "myFirstCorrelationKey"',
          '"messageName": "Message_5",',
        ],
      });
    });
  });

  test('Migrated gateways', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateDiagramPage,
  }) => {
    const targetBpmnProcessId = testProcesses.processV3.bpmnProcessId;
    const targetVersion = testProcesses.processV3.version.toString();

    await test.step('Navigate to first migrated process instance', async () => {
      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessesPage.resultsText.first()).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 4,
      });

      await operateProcessesPage.clickProcessInstanceLink();
      await operateDiagramPage.resetDiagramZoomButton.click();
    });

    await test.step('Verify Event based gateway correlation key update', async () => {
      await operateDiagramPage.verifyFlowNodeMetadata('EventBasedGateway2', {
        expectedText: [
          '"correlationKey": "myFirstCorrelationKey"',
          '"messageName": "Message_3",',
        ],
      });
    });

    await test.step('Verify Exclusive gateway incident migration', async () => {
      await operateDiagramPage.clickFlowNode('ExclusiveGateway2');
      await expect(operateDiagramPage.popoverIncidentHeading).toBeVisible();
    });
  });

  test('Migrated signal elements', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateDiagramPage,
  }) => {
    const targetBpmnProcessId = testProcesses.processV3.bpmnProcessId;
    const targetVersion = testProcesses.processV3.version.toString();

    await test.step('Navigate to migrated process instance', async () => {
      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessesPage.resultsText.first()).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 4,
      });

      await operateProcessesPage.clickProcessInstanceLink();
      await operateDiagramPage.resetDiagramZoomButton.click();
    });

    await test.step('Verify signal intermediate catch event migration', async () => {
      await operateDiagramPage.verifyFlowNodeMetadata(
        'SignalIntermediateCatch2',
        {
          expectedText: 'endDate',
          hiddenText: '"endDate": "null"',
        },
      );
    });
  });

  test('Migrated multi instance elements', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateDiagramPage,
  }) => {
    const targetBpmnProcessId = testProcesses.processV3.bpmnProcessId;
    const targetVersion = testProcesses.processV3.version.toString();

    await test.step('Navigate to migrated process instance', async () => {
      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('3 results')).toBeVisible({
            timeout: 30000,
          });
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await operateProcessesPage.clickProcessInstanceLink();
      await operateDiagramPage.resetDiagramZoomButton.click();
    });

    await test.step('Verify multi instance sub process migration', async () => {
      await operateDiagramPage.verifyFlowNodeMetadata(
        'MultiInstanceSubProcess2',
        {
          expectedText: 'endDate',
          hiddenText: '"endDate": "null"',
          isSubProcess: true,
        },
      );
    });

    await test.step('Verify multi instance task migration', async () => {
      const executionCount =
        operateDiagramPage.getStateOverlay('MultiInstanceTask2');
      await expect(executionCount.locator('span')).toHaveText('2');
    });
  });

  test('Verify migrated tag on process instance', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateProcessInstancePage,
  }) => {
    const targetBpmnProcessId = testProcesses.processV3.bpmnProcessId;
    const targetVersion = testProcesses.processV3.version.toString();

    await test.step('Navigate to target process instances and apply filter to retrieve processes', async () => {
      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await expect(page.getByText('3 results')).toBeVisible({timeout: 30000});
    });

    await test.step('Open first migrated instance', async () => {
      await operateProcessesPage.clickProcessInstanceLink();
    });

    await test.step('Verify migrated tag is visible on the instance', async () => {
      await expect(operateProcessInstancePage.migratedTag).toBeVisible();
    });
  });
});
