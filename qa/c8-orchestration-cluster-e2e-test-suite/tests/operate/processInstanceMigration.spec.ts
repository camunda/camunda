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
  createInstances,
  cancelProcessInstance,
} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';
import {waitForAssertion} from 'utils/waitForAssertion';
import {
  CreateProcessInstanceResponse,
  DeployResourceResponse,
} from '@camunda8/sdk/dist/c8/lib/C8Dto';
import type {TaskCard} from '@pages/TaskPanelPage';
import {parseAssignedTasksFromFile} from 'utils/bpmn';

const PROCESS_INSTANCE_COUNT = 10;
const AUTO_MIGRATION_INSTANCE_COUNT = 6;
const MANUAL_MIGRATION_INSTANCE_COUNT = 3;
const PARALLEL_INSTANCE_COUNT = 1;

type ProcessDeployment = {
  readonly bpmnProcessId: string;
  readonly version: number;
};

type TestProcesses = {
  readonly processV1: ProcessDeployment;
  readonly processV2: ProcessDeployment;
  readonly processV3: ProcessDeployment;
};

type TestParallelProcesses = {
  readonly parallelProcessV1: ProcessDeployment;
  readonly parallelProcessV2: ProcessDeployment;
};

let testProcesses: TestProcesses;
let testParallelProcesses: TestParallelProcesses;
let processes: CreateProcessInstanceResponse[][] = [];
let parallelProcesses: CreateProcessInstanceResponse[] = [];

// Task cards parsed from BPMN fixtures — single source of truth for task names and assignees.
const sourceTaskCards: TaskCard[] = parseAssignedTasksFromFile(
  './resources/parallel_tasks_jw_v1.bpmn',
);

const targetTaskCards: TaskCard[] = parseAssignedTasksFromFile(
  './resources/parallel_tasks_jw_v2.bpmn',
);

test.describe.serial('Process Instance Migration', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/orderProcessMigration_v_1.bpmn']);
    const processV1: ProcessDeployment = {
      bpmnProcessId: 'orderProcessMigration',
      version: 1,
    };

    processes = await Promise.all(
      [...new Array(PROCESS_INSTANCE_COUNT)].map((_, index) =>
        createInstances(processV1.bpmnProcessId, processV1.version, 1, {
          key1: 'myFirstCorrelationKey',
          key2: 'mySecondCorrelationKey',
          key3: `myCorrelationKey${index}`,
        }),
      ),
    );

    const newProcessMigrationV2: DeployResourceResponse = await deploy([
      './resources/orderProcessMigration_v_2.bpmn',
    ]);
    const processV2: ProcessDeployment = {
      bpmnProcessId: 'orderProcessMigration',
      version: newProcessMigrationV2.processes[0].processDefinitionVersion,
    };
    const newProcessMigrationV3: DeployResourceResponse = await deploy([
      './resources/orderProcessMigration_v_3.bpmn',
    ]);
    expect(processV2.version).toBeGreaterThan(processV1.version);

    const processV3: ProcessDeployment = {
      bpmnProcessId: 'newOrderProcessMigration',
      version: newProcessMigrationV3.processes[0].processDefinitionVersion,
    };

    testProcesses = {
      processV1,
      processV2,
      processV3,
    };
    await sleep(2000);
  });

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

  test.afterAll(async () => {
    for (const process of processes) {
      for (const instance of process) {
        await cancelProcessInstance(instance.processInstanceKey as string);
      }
    }
  });

  test('Auto mapping migration', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateProcessMigrationModePage,
  }) => {
    test.slow();
    const sourceVersion = testProcesses.processV1.version.toString();
    const sourceBpmnProcessId = testProcesses.processV1.bpmnProcessId;
    const targetVersion = testProcesses.processV2.version.toString();
    const targetBpmnProcessId = testProcesses.processV2.bpmnProcessId;

    await test.step('Filter by process name and version', async () => {
      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);

      // Wait for the process to be selected and version dropdown to be populated
      await sleep(1000);

      // Ensure we're selecting the correct source version (version 1)
      await operateFiltersPanelPage.selectVersion(sourceVersion);

      await expect
        .poll(() => operateFiltersPanelPage.processVersionFilter.innerText())
        .toBe(sourceVersion);

      await expect(operateProcessesPage.resultsText.first()).toBeVisible({
        timeout: 30000,
      });
    });

    await test.step('Select first 6 process instances for migration', async () => {
      await operateProcessesPage.selectProcessInstances(
        AUTO_MIGRATION_INSTANCE_COUNT,
      );

      await operateProcessesPage.startMigration();
    });

    await test.step('Verify target process is preselected with auto-mapping and Complete Migration', async () => {
      await operateProcessMigrationModePage.targetProcessCombobox.click();
      await operateProcessMigrationModePage
        .getOptionByName(targetBpmnProcessId)
        .click();
      await expect(
        operateProcessMigrationModePage.targetProcessCombobox,
      ).toHaveValue(targetBpmnProcessId);

      await expect(
        operateProcessMigrationModePage.targetVersionDropdown,
      ).toHaveText(targetVersion, {
        useInnerText: true,
      });

      await operateProcessMigrationModePage.verifyFlowNodeMappings([
        {
          label: 'target element for check payment',
          targetValue: 'checkPayment',
        },
        {
          label: 'target element for ship articles',
          targetValue: 'shipArticles',
        },
        {
          label: 'target element for request for payment',
          targetValue: 'requestForPayment',
        },
        {label: 'target element for task a', targetValue: 'TaskA'},
        {label: 'target element for task b', targetValue: 'TaskB'},
        {label: 'target element for task c', targetValue: 'TaskC'},
        {label: 'target element for task d', targetValue: 'TaskD'},
        {
          label: 'target element for message interrupting',
          targetValue: 'MessageInterrupting',
        },
        {
          label: 'target element for timer interrupting',
          targetValue: 'TimerInterrupting',
        },
        {
          label: 'target element for message non-interrupting',
          targetValue: 'MessageNonInterrupting',
        },
        {
          label: 'target element for timer non-interrupting',
          targetValue: 'TimerNonInterrupting',
        },
        {
          label: /target element for message intermediate catch$/i,
          targetValue: 'MessageIntermediateCatch',
        },
        {
          label: /target element for timer intermediate catch$/i,
          targetValue: 'TimerIntermediateCatch',
        },
        {
          label: 'target element for message event sub process',
          targetValue: 'MessageEventSubProcess',
        },
        {
          label: 'target element for timer event sub process',
          targetValue: 'TimerEventSubProcess',
        },
        {label: 'target element for task e', targetValue: 'TaskE'},
        {label: 'target element for task f', targetValue: 'TaskF'},
        {
          label: 'target element for message receive task',
          targetValue: 'MessageReceiveTask',
        },
        {
          label: 'target element for business rule task',
          targetValue: 'BusinessRuleTask',
        },
        {label: 'target element for script task', targetValue: 'ScriptTask'},
        {label: 'target element for send task', targetValue: 'SendTask'},
        {
          label: 'target element for timer start event',
          targetValue: 'TimerStartEvent',
        },
        {
          label: 'target element for signal start event',
          targetValue: 'SignalStartEvent',
        },
        {
          label: 'target element for signal boundary event',
          targetValue: 'SignalBoundaryEvent',
        },
        {
          label: 'target element for signal intermediate catch',
          targetValue: 'SignalIntermediateCatch',
        },
        {
          label: 'target element for signal event sub process',
          targetValue: 'SignalEventSubProcess',
        },
        {
          label: 'target element for error event sub process',
          targetValue: 'ErrorEventSubProcess',
        },
        {
          label: 'target element for error start event',
          targetValue: 'ErrorStartEvent',
        },
        {label: 'target element for task g', targetValue: 'TaskG'},
        {label: 'target element for sub process', targetValue: 'SubProcess'},
        {
          label: 'target element for multi instance sub process',
          targetValue: 'MultiInstanceSubProcess',
        },
        {
          label: 'target element for multi instance task',
          targetValue: 'MultiInstanceTask',
        },
        {
          label: 'target element for compensation task',
          targetValue: 'CompensationTask',
        },
        {
          label: 'target element for compensation boundary event',
          targetValue: 'CompensationBoundaryEvent',
        },
        {
          label: 'target element for message start event',
          targetValue: 'MessageStartEvent',
        },
        {
          label: 'target element for ad hoc sub process',
          targetValue: 'AdHocSubProcess',
        },
        {label: 'target element for task i', targetValue: 'TaskI'},
        {
          label:
            'target element for parallel multi instance ad hoc sub process',
          targetValue: 'ParallelMultiInstanceAdHocSubProcess',
        },
        {label: 'target element for task j', targetValue: 'TaskJ'},
        {
          label:
            'target element for sequential multi instance ad hoc sub process',
          targetValue: 'SequentialMultiInstanceAdHocSubProcess',
        },
        {label: 'target element for task k', targetValue: 'TaskK'},
        {
          label: 'target element for ai agent task',
          targetValue: 'AIAgentTask',
        },
        {
          label: 'target element for agent tools',
          targetValue: 'AgentTools',
        },
        {
          label: 'target element for ai agent sub process',
          targetValue: 'AIAgentsubprocess',
        },
      ]);

      await operateProcessMigrationModePage.completeProcessInstanceMigration();

      await sleep(500);
    });

    await test.step('Verify 6 instances migrated to target version', async () => {
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('6 results')).toBeVisible({
            timeout: 3000,
          });
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await expect(
        operateProcessesPage.versionCells(targetVersion),
      ).toHaveCount(6, {
        timeout: 30000,
      });
    });
  });

  test('Migrated event sub processes', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
  }) => {
    const targetBpmnProcessId = testProcesses.processV2.bpmnProcessId;
    const targetVersion = testProcesses.processV2.version.toString();

    await test.step('Navigate to processes page', async () => {
      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await expect(operateProcessesPage.resultsText.first()).toBeVisible({
        timeout: 30000,
      });
    });

    await test.step('Verify TaskF instances', async () => {
      await page.goto(
        `operate/processes?active=true&incidents=true&processDefinitionId=${targetBpmnProcessId}&processDefinitionVersion=${targetVersion}&elementId=TaskF`,
      );

      await expect(page.getByText('6 results')).toBeVisible({timeout: 90000});
    });
  });

  test('Migrated ad hoc sub processes', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
  }) => {
    const targetBpmnProcessId = testProcesses.processV2.bpmnProcessId;
    const targetVersion = testProcesses.processV2.version.toString();

    await test.step('Navigate to processes page', async () => {
      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await expect(operateProcessesPage.resultsText.first()).toBeVisible({
        timeout: 30000,
      });
    });

    const tasksToVerify = ['TaskI', 'TaskJ', 'TaskK'];
    for (const taskId of tasksToVerify) {
      await test.step(`Verify ${taskId} instances`, async () => {
        await page.goto(
          `operate/processes?active=true&incidents=true&processDefinitionId=${targetBpmnProcessId}&processDefinitionVersion=${targetVersion}&elementId=${taskId}`,
        );

        await expect(page.getByText('6 results')).toBeVisible({timeout: 90000});
      });
    }
  });

  test('Migrated ai agent task and ad hoc sub processes', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
  }) => {
    const targetBpmnProcessId = testProcesses.processV2.bpmnProcessId;
    const targetVersion = testProcesses.processV2.version.toString();

    await test.step('Navigate to processes page', async () => {
      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await expect(operateProcessesPage.resultsText.first()).toBeVisible({
        timeout: 30000,
      });
    });

    const tasksToVerify = ['AIAgentTask', 'AIAgentsubprocess'];
    for (const taskId of tasksToVerify) {
      await test.step(`Verify ${taskId} instances`, async () => {
        await page.goto(
          `operate/processes?active=true&incidents=true&processDefinitionId=${targetBpmnProcessId}&processDefinitionVersion=${targetVersion}&elementId=${taskId}`,
        );

        await expect(page.getByText('6 results')).toBeVisible({
          timeout: 90000,
        });
      });
    }
  });

  test('Manual mapping migration', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateProcessMigrationModePage,
  }) => {
    test.slow();
    const sourceVersion = testProcesses.processV2.version.toString();
    const sourceBpmnProcessId = testProcesses.processV2.bpmnProcessId;
    const targetVersion = testProcesses.processV3.version.toString();
    const targetBpmnProcessId = testProcesses.processV3.bpmnProcessId;

    await test.step('Filter by process name and version', async () => {
      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(sourceVersion);

      await expect(operateProcessesPage.resultsText.first()).toBeVisible({
        timeout: 30000,
      });
    });

    await test.step('Select 3 process instances for migration', async () => {
      await operateProcessesPage.selectProcessInstances(
        MANUAL_MIGRATION_INSTANCE_COUNT,
      );

      await operateProcessesPage.startMigration();
    });

    await test.step('Manually select target process and version', async () => {
      // Add retries and waits for migration modal interactions
      await operateProcessMigrationModePage.targetProcessCombobox.click();
      await sleep(1000);
      await operateProcessMigrationModePage
        .getOptionByName(targetBpmnProcessId)
        .click();

      // Wait for version dropdown to populate after process selection
      await sleep(2000);
      await operateProcessMigrationModePage.targetVersionDropdown.click();
      await sleep(1000);
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
      await operateProcessMigrationModePage.mapFlowNode('Task I', 'Task I2');
      await operateProcessMigrationModePage.mapFlowNode('Task J', 'Task J2');
      await operateProcessMigrationModePage.mapFlowNode('Task K', 'Task K2');

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
      await operateProcessMigrationModePage.mapFlowNode(
        'Ad hoc sub process',
        'Ad hoc sub process 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Parallel multi instance Ad hoc sub process',
        'Parallel multi instance Ad hoc sub process 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Sequential multi instance Ad hoc sub process',
        'Sequential multi instance Ad hoc sub process 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'AI agent Task',
        'AI agent Task 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'Agent tools',
        'Agent tools 2',
      );
      await operateProcessMigrationModePage.mapFlowNode(
        'AI Agent sub process',
        'AI Agent sub process 2',
      );
    });

    await test.step('Proceed to summary and verify migration details', async () => {
      await operateProcessMigrationModePage.clickNextButton();
      await operateProcessMigrationModePage.verifySummaryNotification({
        instanceCount: MANUAL_MIGRATION_INSTANCE_COUNT,
        sourceBpmnProcessId,
        sourceVersion,
        targetBpmnProcessId,
        targetVersion,
      });
    });

    await test.step('Confirm migration and type MIGRATE', async () => {
      await operateProcessMigrationModePage.clickConfirmButton();
      await operateProcessMigrationModePage.fillMigrationConfirmation(
        'MIGRATE',
      );
      await operateProcessMigrationModePage.clickMigrationConfirmationButton();
      await sleep(2000);
    });

    await test.step('Verify 3 instances migrated to target version', async () => {
      // Add retry logic for target version selection and validation
      await waitForAssertion({
        assertion: async () => {
          await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
          // Wait for version dropdown to populate
          await sleep(2000);
          await operateFiltersPanelPage.selectVersion(targetVersion);
          await expect(
            operateProcessesPage.versionCells(targetVersion),
          ).toHaveCount(3, {timeout: 10000});
        },
        onFailure: async () => {
          await page.reload();
          await sleep(1000);
        },
        maxRetries: 3,
      });
    });

    await test.step('Verify remaining instances still at source version', async () => {
      // Add retry logic for version selection which can be flaky in CI
      await waitForAssertion({
        assertion: async () => {
          await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
          // Wait for version dropdown to populate before selecting
          await sleep(2000);
          await operateFiltersPanelPage.selectVersion(sourceVersion);
          await expect(page.getByText('3 results')).toBeVisible({
            timeout: 15000,
          });
        },
        onFailure: async () => {
          await page.reload();
          await sleep(1000);
        },
        maxRetries: 3,
      });
    });
  });

  test('Migrated process instances', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateProcessInstancePage,
    operateDiagramPage,
  }) => {
    const targetBpmnProcessId = testProcesses.processV3.bpmnProcessId;
    const targetVersion = testProcesses.processV3.version.toString();

    await test.step('Navigate to migrated process instance', async () => {
      await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(targetVersion);

      // Use waitForAssertion for better CI reliability
      await waitForAssertion({
        assertion: async () => {
          await expect(operateProcessesPage.resultsText).toContainText(
            '3 results',
            {timeout: 10000},
          );
        },
        onFailure: async () => {
          await page.reload();
          await operateFiltersPanelPage.selectProcess(targetBpmnProcessId);
          await operateFiltersPanelPage.selectVersion(targetVersion);
        },
        maxRetries: 5,
      });

      await operateProcessesPage.clickProcessInstanceLink();
    });

    await test.step('Verify migrated tag is visible on the instance', async () => {
      await expect(operateProcessInstancePage.migratedTag).toBeVisible({
        timeout: 30000,
      });
    });

    await test.step('Verify element instances migration', async () => {
      // Wait for diagram to load before checking overlays
      await sleep(2000);

      await operateDiagramPage.verifyStateOverlay('SendTask2', 'active', 1);
      await operateDiagramPage.verifyStateOverlay('TaskG', 'active', 1);
      await operateDiagramPage.verifyStateOverlay(
        'BusinessRuleTask2',
        'incidents',
        1,
      );
      await operateDiagramPage.verifyStateOverlay('TaskA2', 'active', 1);
      await operateDiagramPage.verifyStateOverlay('TaskC2', 'active', 1);
      await operateDiagramPage.verifyStateOverlay(
        'MessageReceiveTask2',
        'active',
        1,
      );
      await operateDiagramPage.verifyStateOverlay(
        'MessageIntermediateCatch2',
        'active',
        1,
      );
      await operateDiagramPage.verifyStateOverlay(
        'EventBasedGateway2',
        'active',
        1,
      );
      await operateDiagramPage.verifyStateOverlay(
        'ExclusiveGateway2',
        'incidents',
        1,
      );
      await operateDiagramPage.verifyStateOverlay(
        'SignalIntermediateCatch2',
        'active',
        1,
      );
      await operateDiagramPage.verifyStateOverlay(
        'MultiInstanceSubProcess2',
        'active',
        2,
      );
      await operateDiagramPage.verifyStateOverlay(
        'MultiInstanceTask2',
        'active',
        2,
      );
    });

    await test.step('Verify Business rule task incident migration', async () => {
      await operateProcessInstancePage.verifyIncidents(
        ['Called decision error.'],
        'BusinessRuleTask2',
      );
    });

    await test.step('Verify Exclusive gateway incident migration', async () => {
      await operateProcessInstancePage.verifyIncidents(
        ['Extract value error.'],
        'ExclusiveGateway2',
      );
    });
  });
});

test.describe('Parallel job-based user task migration', () => {
  test.beforeAll(async () => {
    const parallelV1Deploy: DeployResourceResponse = await deploy([
      './resources/parallel_tasks_jw_v1.bpmn',
      './resources/job-worker-id-form.form',
    ]);
    const parallelProcessV1: ProcessDeployment = {
      bpmnProcessId: parallelV1Deploy.processes[0].processDefinitionId,
      version: parallelV1Deploy.processes[0].processDefinitionVersion,
    };

    parallelProcesses = await createInstances(
      parallelProcessV1.bpmnProcessId,
      parallelProcessV1.version,
      PARALLEL_INSTANCE_COUNT,
    );

    // Redeploying with the same bpmnProcessId — Zeebe auto-increments the version to V2.
    const parallelV2Deploy: DeployResourceResponse = await deploy([
      './resources/parallel_tasks_jw_v2.bpmn',
    ]);
    const parallelProcessV2: ProcessDeployment = {
      bpmnProcessId: parallelV2Deploy.processes[0].processDefinitionId,
      version: parallelV2Deploy.processes[0].processDefinitionVersion,
    };

    expect(parallelProcessV2.version).toBeGreaterThan(
      parallelProcessV1.version,
    );

    testParallelProcesses = {parallelProcessV1, parallelProcessV2};
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test.afterAll(async () => {
    await Promise.all(
      parallelProcesses.map((p) => cancelProcessInstance(p.processInstanceKey)),
    );
  });

  test('Migrate parallel job-based user tasks to Camunda user tasks', async ({
    page,
    operateHomePage,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateProcessMigrationModePage,
    tasklistHeader,
    taskPanelPage,
    taskDetailsPage,
    loginPage,
  }) => {
    test.slow();

    const sourceVersion =
      testParallelProcesses.parallelProcessV1.version.toString();
    const targetVersion =
      testParallelProcesses.parallelProcessV2.version.toString();
    const bpmnProcessId = testParallelProcesses.parallelProcessV1.bpmnProcessId;
    // totalV2InstanceCount = original V2 instances + migrated V1→V2 instances
    const totalV2InstanceCount = 2 * PARALLEL_INSTANCE_COUNT;

    // Create V2 instances (Camunda user tasks) so Tasklist has tasks to assert on
    // before migration — V1 job-based tasks are not visible in Tasklist.
    const v2Instances = await createInstances(
      testParallelProcesses.parallelProcessV2.bpmnProcessId,
      testParallelProcesses.parallelProcessV2.version,
      PARALLEL_INSTANCE_COUNT,
    );
    // Track V2 instances so afterAll cancels them on test failure.
    parallelProcesses.push(...v2Instances);

    await test.step('Verify Tasklist tasks before migration', async () => {
      await navigateToApp(page, 'tasklist');
      await loginPage.login('demo', 'demo');
      await tasklistHeader.clickTasksTab();
      await taskPanelPage.filterBy('All open tasks');

      // Poll until Camunda user tasks from V2 instances are indexed in Tasklist.
      await waitForAssertion({
        assertion: async () => {
          // V2 instances have Camunda user tasks visible in Tasklist;
          // V1 instances have job-based tasks which are not shown.
          await taskPanelPage.assertTaskCardsPresent(targetTaskCards, {
            expectedCount: PARALLEL_INSTANCE_COUNT,
          });
          await taskPanelPage.assertTaskCardsAbsent(sourceTaskCards);
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 10,
      });
    });

    await test.step('Filter by source process and version 1', async () => {
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.selectProcess(bpmnProcessId);

      // Wait for the version dropdown to be fully populated after process selection.
      await sleep(1000);

      await operateFiltersPanelPage.selectVersion(sourceVersion);
      await expect(operateProcessesPage.resultsText.first()).toBeVisible({
        timeout: 30000,
      });
    });

    await test.step(`Select ${PARALLEL_INSTANCE_COUNT} instance(s) and start migration`, async () => {
      await operateProcessesPage.selectProcessInstances(
        PARALLEL_INSTANCE_COUNT,
      );
      await operateProcessesPage.startMigration();
    });

    await test.step('Select target version 2 (same process)', async () => {
      await expect(
        operateProcessMigrationModePage.targetProcessCombobox,
      ).toHaveValue(bpmnProcessId);

      await operateProcessMigrationModePage.targetVersionDropdown.click();
      await operateProcessMigrationModePage
        .getOptionByName(targetVersion)
        .click();
      await expect(
        operateProcessMigrationModePage.targetVersionDropdown,
      ).toHaveText(targetVersion, {useInnerText: true});
    });

    await test.step('Map renamed flow node: Embedded → Embedded new', async () => {
      await operateProcessMigrationModePage.mapFlowNode(
        'Embedded',
        'Embedded new',
      );
    });

    await test.step('Verify auto-mapped flow nodes', async () => {
      await operateProcessMigrationModePage.verifyFlowNodeMappings([
        {
          label: /target element for camunda form/i,
          targetValue: 'camunda_form',
        },
        {
          label: /target element for external form/i,
          targetValue: 'external_form',
        },
      ]);
    });

    await test.step('Review summary and confirm migration', async () => {
      await operateProcessMigrationModePage.clickNextButton();

      await operateProcessMigrationModePage.verifySummaryNotification({
        instanceCount: PARALLEL_INSTANCE_COUNT,
        sourceBpmnProcessId: bpmnProcessId,
        sourceVersion,
        targetBpmnProcessId: bpmnProcessId,
        targetVersion,
      });

      await operateProcessMigrationModePage.clickConfirmButton();
      await operateProcessMigrationModePage.fillMigrationConfirmation(
        'MIGRATE',
      );
      await operateProcessMigrationModePage.clickMigrationConfirmationButton();
    });

    await test.step('Verify migrated instances appear in Operate at version 2', async () => {
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.selectProcess(bpmnProcessId);

      // Wait for the version dropdown to be fully populated after process selection.
      await sleep(1000);

      await operateFiltersPanelPage.selectVersion(targetVersion);

      await waitForAssertion({
        assertion: async () => {
          await expect(
            page.getByText(`${totalV2InstanceCount} results`),
          ).toBeVisible({
            timeout: 3000,
          });
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await expect(
        operateProcessesPage.versionCells(targetVersion),
      ).toHaveCount(totalV2InstanceCount, {timeout: 30000});
    });

    await test.step('Navigate to Tasklist and verify migrated Camunda user task cards with assignees', async () => {
      await navigateToApp(page, 'tasklist');
      await loginPage.login('demo', 'demo');
      await tasklistHeader.clickTasksTab();
      await taskPanelPage.filterBy('All open tasks');

      // Poll until migrated Camunda user tasks are indexed in Tasklist.
      await waitForAssertion({
        assertion: async () => {
          await taskPanelPage.assertTaskCardsPresent(targetTaskCards, {
            expectedCount: totalV2InstanceCount,
          });
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 10,
      });
    });

    await test.step('Open each task, unassign, assign to self, and complete', async () => {
      const taskNames = targetTaskCards.map((task) => task.name);

      for (const taskName of taskNames) {
        for (let i = 0; i < totalV2InstanceCount; i++) {
          // Always click .nth(0) — the completed task disappears so the next one shifts up
          await taskPanelPage.availableTasks
            .getByText(taskName, {exact: true})
            .nth(0)
            .click();

          await taskDetailsPage.unassignReassignToMeAndComplete();
        }
      }

      // After all tasks complete, the result (manualTask) auto-fires and all instances end.
      await taskPanelPage.filterBy('All open tasks');
      await expect(
        taskPanelPage.availableTasks.getByText(bpmnProcessId),
      ).toHaveCount(0);
    });

    await test.step(`Verify ${totalV2InstanceCount} process instance(s) are completed in Operate`, async () => {
      await navigateToApp(page, 'operate');
      await loginPage.login('demo', 'demo');
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.selectProcess(bpmnProcessId);

      // Wait for the version dropdown to be fully populated after process selection.
      await sleep(1000);

      await operateFiltersPanelPage.selectVersion(targetVersion);
      await operateFiltersPanelPage.clickCompletedInstancesCheckbox();

      await waitForAssertion({
        assertion: async () => {
          await expect(
            page.getByText(`${totalV2InstanceCount} results`),
          ).toBeVisible({
            timeout: 3000,
          });
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });
  });
});
