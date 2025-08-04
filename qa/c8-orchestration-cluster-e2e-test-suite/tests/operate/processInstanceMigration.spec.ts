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
  createWorker,
  createInstances,
  createSingleInstance,
  ProcessDeployment,
} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {waitForAssertion} from 'utils/waitForAssertion';
import {sleep} from 'utils/sleep';

// const {createWorker} = zeebeGrpcApi;
// let initialData: Awaited<ReturnType<typeof setup>>;

let processV1Instances: any[];
let processV1: ProcessDeployment;
let processV2: ProcessDeployment;
let processV3: ProcessDeployment;

test.beforeAll(async ({request}) => {
  const {deployments: deploymentsV1} = await deploy([
    './resources/orderProcessMigration_v_1.bpmn',
  ]);

  processV1Instances = await Promise.all(
    [...new Array(10)].map((_, index) => {
      const deployment = deploymentsV1[0];
      if ('processDefinition' in deployment && deployment.processDefinition) {
        return createSingleInstance(
          'orderProcessMigration',
          (deployment.processDefinition as unknown as ProcessDeployment)
            .processDefinitionVersion,
          {
            key1: 'myFirstCorrelationKey',
            key2: 'mySecondCorrelationKey',
            key3: `myCorrelationKey${index}`,
          },
        );
      }
      throw new Error('Deployment does not contain a processDefinition');
    }),
  );
  await sleep(500);
  const {deployments: deploymentsV2} = await deploy([
    './resources/orderProcessMigration_v_2.bpmn',
  ]);
  const {deployments: deploymentsV3} = await deploy([
    './resources/orderProcessMigration_v_3.bpmn',
  ]);

  createWorker('failingTaskWorker', true, {}, (job) => {
    return job.fail('expected worker failure');
  });

  if (
    !('processDefinition' in deploymentsV1[0]) ||
    !deploymentsV1[0].processDefinition
  ) {
    throw new Error(
      'ProcessV1 deployment does not contain a processDefinition',
    );
  }
  if (
    !('processDefinition' in deploymentsV2[0]) ||
    !deploymentsV2[0].processDefinition
  ) {
    throw new Error(
      'ProcessV2 deployment does not contain a processDefinition',
    );
  }
  if (
    !('processDefinition' in deploymentsV3[0]) ||
    !deploymentsV3[0].processDefinition
  ) {
    throw new Error(
      'ProcessV3 deployment does not contain a processDefinition',
    );
  }

  processV1 = deploymentsV1[0]
    .processDefinition as unknown as ProcessDeployment;
  processV2 = deploymentsV2[0]
    .processDefinition as unknown as ProcessDeployment;
  processV3 = deploymentsV3[0]
    .processDefinition as unknown as ProcessDeployment;
});

test.describe.serial('Process Instance Migration', () => {
  /**
   * Migrate from ProcessV1 to ProcessV2
   * ProcessV1 and ProcessV2 have identical bpmnProcess id and flow node names,
   * so the target process will be preselected and flow nodes will be auto-mapped
   */
  test('Auto Mapping @roundtrip', async ({
    operateProcessesPage,
    operateFiltersPanelPage,
    loginPage,
    operateHomePage,
    operateMigrationView,
    // commonPage,
    page,
  }) => {
    test.slow();

    const sourceVersion = processV1.processDefinitionVersion.toString();
    const sourceBpmnProcessId = processV1.processDefinitionId;
    const targetVersion = processV2.processDefinitionVersion.toString();
    const targetBpmnProcessId = processV2.processDefinitionId;

    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();

    await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
    await operateFiltersPanelPage.selectVersion(sourceVersion);

    await waitForAssertion({
      assertion: async () => {
        await expect(page.getByText('results')).toBeVisible();
      },
      onFailure: async () => {
        await page.reload();
      },
    });

    // select 6 process instances for migration
    await operateProcessesPage.getNthProcessInstanceCheckbox(0).click();
    await operateProcessesPage.getNthProcessInstanceCheckbox(1).click();
    await operateProcessesPage.getNthProcessInstanceCheckbox(2).click();
    await operateProcessesPage.getNthProcessInstanceCheckbox(3).click();
    await operateProcessesPage.getNthProcessInstanceCheckbox(4).click();
    await operateProcessesPage.getNthProcessInstanceCheckbox(5).click();

    await operateProcessesPage.migrateButton.click();
    await operateProcessesPage.migrationModal.continueButton.click();

    // Expect auto mapping for each flow node
    await expect(page.getByLabel(/target flow node for/i)).toHaveCount(48);

    await expect(
      page.getByLabel(/target flow node for check payment/i),
    ).toHaveValue('checkPayment');
    await expect(
      page.getByLabel(/target flow node for ship articles/i),
    ).toHaveValue('shipArticles');
    await expect(
      page.getByLabel(/target flow node for request for payment/i),
    ).toHaveValue('requestForPayment');
    await expect(page.getByLabel(/target flow node for task a/i)).toHaveValue(
      'TaskA',
    );
    await expect(page.getByLabel(/target flow node for task b/i)).toHaveValue(
      'TaskB',
    );
    await expect(page.getByLabel(/target flow node for task c/i)).toHaveValue(
      'TaskC',
    );
    await expect(page.getByLabel(/target flow node for task d/i)).toHaveValue(
      'TaskD',
    );
    await expect(
      page.getByLabel(/target flow node for message interrupting/i),
    ).toHaveValue('MessageInterrupting');
    await expect(
      page.getByLabel(/target flow node for timer interrupting/i),
    ).toHaveValue('TimerInterrupting');
    await expect(
      page.getByLabel(/target flow node for message non-interrupting/i),
    ).toHaveValue('MessageNonInterrupting');
    await expect(
      page.getByLabel(/target flow node for timer non-interrupting/i),
    ).toHaveValue('TimerNonInterrupting');
    await expect(
      page.getByLabel(/target flow node for message intermediate catch$/i),
    ).toHaveValue('MessageIntermediateCatch');
    await expect(
      page.getByLabel(/target flow node for timer intermediate catch$/i),
    ).toHaveValue('TimerIntermediateCatch');
    await expect(
      page.getByLabel(/target flow node for message event sub process/i),
    ).toHaveValue('MessageEventSubProcess');
    await expect(
      page.getByLabel(/target flow node for timer event sub process/i),
    ).toHaveValue('TimerEventSubProcess');
    await expect(page.getByLabel(/target flow node for task e/i)).toHaveValue(
      'TaskE',
    );
    await expect(page.getByLabel(/target flow node for task f/i)).toHaveValue(
      'TaskF',
    );
    await expect(
      page.getByLabel(/target flow node for message receive task/i),
    ).toHaveValue('MessageReceiveTask');
    await expect(
      page.getByLabel(/target flow node for business rule task/i),
    ).toHaveValue('BusinessRuleTask');
    await expect(
      page.getByLabel(/target flow node for script task/i),
    ).toHaveValue('ScriptTask');
    await expect(
      page.getByLabel(/target flow node for send task/i),
    ).toHaveValue('SendTask');
    await expect(
      page.getByLabel(/target flow node for timer start event/i),
    ).toHaveValue('TimerStartEvent');
    await expect(
      page.getByLabel(/target flow node for signal start event/i),
    ).toHaveValue('SignalStartEvent');
    await expect(
      page.getByLabel(/target flow node for signal boundary event/i),
    ).toHaveValue('SignalBoundaryEvent');
    await expect(
      page.getByLabel(/target flow node for signal intermediate catch/i),
    ).toHaveValue('SignalIntermediateCatch');
    await expect(
      page.getByLabel(/target flow node for signal event sub process/i),
    ).toHaveValue('SignalEventSubProcess');
    await expect(
      page.getByLabel(/target flow node for error event sub process/i),
    ).toHaveValue('ErrorEventSubProcess');
    await expect(
      page.getByLabel(/target flow node for error start event/i),
    ).toHaveValue('ErrorStartEvent');
    await expect(page.getByLabel(/target flow node for task g/i)).toHaveValue(
      'TaskG',
    );
    await expect(
      page.getByLabel(/target flow node for sub process/i),
    ).toHaveValue('SubProcess');
    await expect(
      page.getByLabel(/target flow node for multi instance sub process/i),
    ).toHaveValue('MultiInstanceSubProcess');
    await expect(
      page.getByLabel(/target flow node for multi instance task/i),
    ).toHaveValue('MultiInstanceTask');
    await expect(
      page.getByLabel(/target flow node for compensation task/i),
    ).toHaveValue('CompensationTask');
    await expect(
      page.getByLabel(/target flow node for compensation boundary event/i),
    ).toHaveValue('CompensationBoundaryEvent');
    await expect(
      page.getByLabel(/target flow node for message start event/i),
    ).toHaveValue('MessageStartEvent');

    // Expect pre-selected process and version
    await expect(operateMigrationView.targetProcessComboBox).toHaveValue(
      targetBpmnProcessId,
    );
    await expect(operateMigrationView.targetVersionDropdown).toHaveText(
      targetVersion,
      {useInnerText: true},
    );

    await operateMigrationView.nextButton.click();

    await expect(operateMigrationView.summaryNotification).toContainText(
      `You are about to migrate 6 process instances from the process definition: ${sourceBpmnProcessId} - version ${sourceVersion} to the process definition: ${targetBpmnProcessId} - version ${targetVersion}`,
    );

    await operateMigrationView.confirmButton.click();
    await operateMigrationView.confirmMigration();

    await expect(operateMigrationView.operationsList).toBeVisible();

    const migrateOperationEntry = operateMigrationView.operationsList
      .getByRole('listitem')
      .first();
    await expect(migrateOperationEntry).toContainText('Migrate');
    await expect(migrateOperationEntry.getByRole('progressbar')).toBeVisible();

    // wait for migrate operation to finish
    await expect(
      migrateOperationEntry.getByRole('progressbar'),
    ).not.toBeVisible({timeout: 60000});

    await expect(operateFiltersPanelPage.processNameFilter).toHaveValue(
      targetBpmnProcessId,
    );
    expect(await operateFiltersPanelPage.processVersionFilter.innerText()).toBe(
      targetVersion,
    );

    await migrateOperationEntry.getByRole('link').click();

    await expect(
      operateProcessesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/6 results/i);

    // expect 6 process instances to be migrated to target version
    await expect(
      operateProcessesPage.processInstancesTable.getByRole('cell', {
        name: targetVersion,
        exact: true,
      }),
    ).toHaveCount(6);

    // expect no process instances for source version
    await expect(
      operateProcessesPage.processInstancesTable.getByRole('cell', {
        name: sourceVersion,
        exact: true,
      }),
    ).not.toBeVisible();

    await operateFiltersPanelPage.removeOptionalFilter('Operation Id');

    // expect 4 process instances for source version
    await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
    await operateFiltersPanelPage.selectVersion(sourceVersion);
    await expect(
      operateProcessesPage.processInstancesTable.getByRole('heading'),
    ).toContainText(/4 results/i);

    await operateProcessesPage.collapseOperationsPanel();
  });

  // test('Migrated event sub processes', async ({
  //   commonPage,
  //   operateProcessesPage,
  // }) => {
  //   await operateProcessesPage.gotooperateProcessesPage({
  //     searchParams: {
  //       active: 'true',
  //       incidents: 'true',
  //     },
  //   });
  //   await commonPage.expandOperationsPanel();

  //   const migrateOperationEntry = commonPage.operationsList
  //     .getByRole('listitem')
  //     .filter({hasText: /^Migrate/i})
  //     .first();

  //   const operationId = await migrateOperationEntry
  //     .getByRole('link')
  //     .innerText();

  //   await operateProcessesPage.gotooperateProcessesPage({
  //     searchParams: {
  //       active: 'true',
  //       incidents: 'true',
  //       process: processV2.bpmnProcessId,
  //       version: processV2.version.toString(),
  //       operationId,
  //       flowNodeId: 'TaskE',
  //     },
  //   });

  //   await expect(
  //     operateProcessesPage.processInstancesTable.getByRole('heading'),
  //   ).toContainText(/6 results/i);
  // });

  // /**
  //  * Migrate from ProcessV2 to ProcessV3
  //  * ProcessV3 has a different bpmn process id and different flow node names,
  //  * so no flow node auto-mapping or pre-selected processes are available.
  //  */
  // test('Manual Mapping @roundtrip', async ({
  //   operateProcessesPage,
  //   operateFiltersPanelPage,
  //   migrationView,
  //   commonPage,
  // }) => {
  //   test.slow();

  //   const sourceVersion = processV2.version.toString();
  //   const sourceBpmnProcessId = processV2.bpmnProcessId;
  //   const targetVersion = processV3.version.toString();
  //   const targetBpmnProcessId = processV3.bpmnProcessId;

  //   await operateProcessesPage.gotooperateProcessesPage({
  //     searchParams: {active: 'true', incidents: 'true'},
  //   });

  //   await filtersPanel.selectProcess(sourceBpmnProcessId);
  //   await filtersPanel.selectVersion(sourceVersion);

  //   await expect(
  //     operateProcessesPage.processInstancesTable.getByRole('heading'),
  //   ).toContainText(/results/i);

  //   // select 3 process instances for migration
  //   await operateProcessesPage.getNthProcessInstanceCheckbox(0).click();
  //   await operateProcessesPage.getNthProcessInstanceCheckbox(1).click();
  //   await operateProcessesPage.getNthProcessInstanceCheckbox(2).click();

  //   await operateProcessesPage.migrateButton.click();
  //   await operateProcessesPage.migrationModal.confirmButton.click();

  //   await migrationView.selectTargetProcess(targetBpmnProcessId);
  //   await migrationView.selectTargetVersion(targetVersion);

  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Check payment',
  //     targetFlowNodeName: 'Ship Articles 2',
  //   });

  //   /**
  //    * Map Tasks A-D. Note that there is a cross mapping:
  //    * A -> C2
  //    * C -> A2
  //    */
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Task A',
  //     targetFlowNodeName: 'Task C2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Task B',
  //     targetFlowNodeName: 'Task B2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Task C',
  //     targetFlowNodeName: 'Task A2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Task D',
  //     targetFlowNodeName: 'Task D2',
  //   });

  //   /**
  //    * Map boundary events. Note that there is a cross mapping:
  //    * interrupting <-> non-interrupting
  //    */
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Message interrupting',
  //     targetFlowNodeName: 'Message non-interrupting 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Timer interrupting',
  //     targetFlowNodeName: 'Timer interrupting 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Message non-interrupting',
  //     targetFlowNodeName: 'Message interrupting 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Timer non-interrupting',
  //     targetFlowNodeName: 'Timer non-interrupting 2',
  //   });

  //   /**
  //    * Map intermediate catch events
  //    */
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Message intermediate catch',
  //     targetFlowNodeName: 'Message intermediate catch 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Timer intermediate catch',
  //     targetFlowNodeName: 'Timer intermediate catch 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Message intermediate catch B',
  //     targetFlowNodeName: 'Message intermediate catch B2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Timer intermediate catch B',
  //     targetFlowNodeName: 'Timer intermediate catch B2',
  //   });

  //   /**
  //    * Map event sub processes with containing tasks
  //    */
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Message event sub process',
  //     targetFlowNodeName: 'Message event sub process 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Timer event sub process',
  //     targetFlowNodeName: 'Timer event sub process 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Task E',
  //     targetFlowNodeName: 'Task E2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Task F',
  //     targetFlowNodeName: 'Task F2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Timer start event',
  //     targetFlowNodeName: 'Timer start event 2',
  //   });

  //   /**
  //    * Map signal elements
  //    */
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Signal intermediate catch',
  //     targetFlowNodeName: 'Signal intermediate catch 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Signal start event',
  //     targetFlowNodeName: 'Signal start event',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Signal boundary event',
  //     targetFlowNodeName: 'Signal boundary event 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Signal event sub process',
  //     targetFlowNodeName: 'Signal event sub process 2',
  //   });

  //   /**
  //    * Map other tasks
  //    */
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Message receive task',
  //     targetFlowNodeName: 'Message receive task 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Business rule task',
  //     targetFlowNodeName: 'Business rule task 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Script task',
  //     targetFlowNodeName: 'Script task 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Send Task',
  //     targetFlowNodeName: 'Send Task 2',
  //   });

  //   /**
  //    * Map gateways
  //    */
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Event based gateway',
  //     targetFlowNodeName: 'Event based gateway 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Exclusive gateway',
  //     targetFlowNodeName: 'Exclusive gateway 2',
  //   });

  //   /**
  //    * Map multi instance elements
  //    */
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Multi instance sub process',
  //     targetFlowNodeName: 'Multi instance sub process 2',
  //   });
  //   await migrationView.mapFlowNode({
  //     sourceFlowNodeName: 'Multi instance task',
  //     targetFlowNodeName: 'Multi instance task 2',
  //   });

  //   await migrationView.nextButton.click();

  //   await expect(migrationView.summaryNotification).toContainText(
  //     `You are about to migrate 3 process instances from the process definition: ${sourceBpmnProcessId} - version ${sourceVersion} to the process definition: ${targetBpmnProcessId} - version ${targetVersion}`,
  //   );

  //   await migrationView.confirmButton.click();
  //   await migrationView.confirmMigration();

  //   await expect(commonPage.operationsList).toBeVisible();

  //   const migrateOperationEntry = commonPage.operationsList
  //     .getByRole('listitem')
  //     .first();

  //   await expect(migrateOperationEntry).toContainText('Migrate');
  //   await expect(migrateOperationEntry.getByRole('progressbar')).toBeVisible();

  //   // wait for migrate operation to finish
  //   await expect(
  //     migrateOperationEntry.getByRole('progressbar'),
  //   ).not.toBeVisible({timeout: 60000});

  //   await expect(filtersPanel.processNameFilter).toHaveValue(
  //     targetBpmnProcessId,
  //   );
  //   expect(await filtersPanel.processVersionFilter.innerText()).toBe(
  //     targetVersion,
  //   );

  //   await migrateOperationEntry.getByRole('link').click();

  //   await expect(
  //     operateProcessesPage.processInstancesTable.getByRole('heading'),
  //   ).toContainText(/3 results/i);

  //   // expect 3 process instances to be migrated to target version
  //   await expect(
  //     operateProcessesPage.processInstancesTable.getByRole('cell', {
  //       name: targetVersion,
  //       exact: true,
  //     }),
  //   ).toHaveCount(3);

  //   // expect no process instances for source version
  //   await expect(
  //     operateProcessesPage.processInstancesTable.getByRole('cell', {
  //       name: sourceVersion,
  //       exact: true,
  //     }),
  //   ).not.toBeVisible();

  //   await filtersPanel.removeOptionalFilter('Operation Id');

  //   // expect 3 process instances for source version
  //   await filtersPanel.selectProcess(sourceBpmnProcessId);
  //   await filtersPanel.selectVersion(sourceVersion);
  //   await expect(
  //     operateProcessesPage.processInstancesTable.getByRole('heading'),
  //   ).toContainText(/3 results/i);

  //   await commonPage.collapseOperationsPanel();
  // });

  // test('Migrated tasks', async ({
  //   operateProcessesPage,
  //   processInstancePage,
  //   page,
  //   request,
  // }) => {
  //   // Wait until all script tasks are in incident state.
  //   // This is needed to ensure that the UI is in the expected state on time.
  //   await expect
  //     .poll(
  //       async () => {
  //         const response = await request.post(
  //           `${config.endpoint}/v1/flownode-instances/search`,
  //           {
  //             data: {
  //               filter: {
  //                 flowNodeId: 'ScriptTask2',
  //                 state: 'ACTIVE',
  //                 incident: true,
  //                 processDefinitionKey: processV3.processDefinitionKey,
  //               },
  //             },
  //           },
  //         );

  //         return await response.json();
  //       },
  //       {timeout: SETUP_WAITING_TIME},
  //     )
  //     .toHaveProperty('total', 3);

  //   await operateProcessesPage.gotooperateProcessesPage({
  //     searchParams: {
  //       active: 'true',
  //       incidents: 'true',
  //       process: processV3.bpmnProcessId,
  //       version: processV3.version.toString(),
  //     },
  //   });

  //   await operateProcessesPage.getNthProcessInstanceLink(0).click();
  //   await processInstancePage.diagram.resetDiagramZoomButton.click();

  //   /**
  //    * Script task
  //    */
  //   await processInstancePage.diagram.clickFlowNode('Script task 2');
  //   await processInstancePage.diagram.showMetaData();
  //   await page.waitForSelector('.monaco-aria-container'); // wait until monaco is fully loaded

  //   /**
  //    * Expect that the script task incident has been migrated.
  //    * The target task "Script task 2" has a FEEL expression which would be
  //    * evaluated immediately which is expected to be overwritten with the incident.
  //    */
  //   await expect(
  //     processInstancePage.metadataModal.getByText('expected worker failure'),
  //   ).toBeVisible();

  //   await processInstancePage.metadataModal
  //     .getByRole('button', {name: /close/i})
  //     .click();
  //   await processInstancePage.diagram.clickFlowNode('Script task 2'); // deselect Script task 2

  //   /**
  //    * Send task
  //    */
  //   await processInstancePage.diagram.clickFlowNode('Send task 2');
  //   await processInstancePage.diagram.showMetaData();

  //   /**
  //    * Expect that the active send task with task worker "foo" has been migrated.
  //    * The target task "Send task 2" has the task type "failingTaskWorker", which would
  //    * end up in an incident state. This is expected to be overwritten with the "foo" worker type.
  //    */
  //   await expect(
  //     processInstancePage.metadataModal.getByText('expected worker failure'),
  //   ).not.toBeVisible();

  //   await processInstancePage.metadataModal
  //     .getByRole('button', {name: /close/i})
  //     .click();
  //   await processInstancePage.diagram.clickFlowNode('Send task 2'); // deselect Script task 2

  //   /**
  //    * Task G
  //    */
  //   await processInstancePage.diagram.clickFlowNode('Task G');
  //   await processInstancePage.diagram.showMetaData();

  //   /**
  //    * Expect that Task G has been migrated. The target Task G is following an error event "error2"
  //    * which is never thrown. Without a successful migration Task G would not be active.
  //    */
  //   await expect(
  //     processInstancePage.metadataModal.getByText('endDate'),
  //   ).toBeVisible();
  //   await expect(
  //     processInstancePage.metadataModal.getByText('"endDate": "null"'),
  //   ).not.toBeVisible();

  //   await processInstancePage.metadataModal
  //     .getByRole('button', {name: /close/i})
  //     .click();
  //   await processInstancePage.diagram.clickFlowNode('Task G'); // deselect

  //   /**
  //    * Business rule task
  //    */
  //   await processInstancePage.diagram.clickFlowNode('Business rule task 2');
  //   await processInstancePage.diagram.showMetaData();

  //   /**
  //    * Expect that the incident for the business rule task has been migrated.
  //    * The target task "Business rule task 2" has a different called decision "invalid2"
  //    * which is expected to be overwritten with the decision key "invalid".
  //    */
  //   await expect(
  //     processInstancePage.metadataModal.getByText(
  //       "Expected to evaluate decision 'invalid', but no decision found for id 'invalid'",
  //     ),
  //   ).toBeVisible();

  //   await processInstancePage.metadataModal
  //     .getByRole('button', {name: /close/i})
  //     .click();
  //   await processInstancePage.diagram.clickFlowNode('Business rule task 2'); // deselect

  //   /**
  //    * Escalation task
  //    */
  //   const escalationWorker = createWorker(
  //     'escalationWorker',
  //     true,
  //     {},
  //     async (job) => {
  //       const acknowledgement = await job.complete();
  //       await escalationWorker.close();
  //       return acknowledgement;
  //     },
  //   );

  //   /**
  //    * Expect that the escalation boundary event has been migrated.
  //    * In v1 and v2 of the process the escalation boundary event has a different escalation code (123).
  //    * Since the escalation code is static, it is expected that the code in the target process (234) is used.
  //    *
  //    * To test this, an escalation throw event with escalation code (234) is triggered. When the escalation
  //    * boundary event becomes selectable, it means it has caught the escalation correctly.
  //    */
  //   await expect(
  //     await processInstancePage.diagram.getLabeledElement(
  //       'Escalation boundary event',
  //     ),
  //   ).toHaveClass(/op-selectable/, {timeout: 20000});

  //   /**
  //    * Escalation event task
  //    */
  //   await processInstancePage.diagram.clickFlowNode('Escalation event task');
  //   await processInstancePage.diagram.showMetaData();

  //   /**
  //    * Expect that escalation event task has been migrated and is active
  //    */
  //   await expect(
  //     processInstancePage.metadataModal.getByText('endDate'),
  //   ).toBeVisible();
  //   await expect(
  //     processInstancePage.metadataModal.getByText('"endDate": "null"'),
  //   ).not.toBeVisible();

  //   await processInstancePage.metadataModal
  //     .getByRole('button', {name: /close/i})
  //     .click();
  //   await processInstancePage.diagram.clickFlowNode('Escalation event task'); // deselect

  //   /**
  //    * Compensation task
  //    */
  //   const compensationWorker = createWorker(
  //     'compensationWorker',
  //     true,
  //     {},
  //     async (job) => {
  //       const acknowledgement = await job.complete();
  //       await compensationWorker.close();
  //       return acknowledgement;
  //     },
  //   );

  //   /**
  //    * Expect that the compensation boundary event has been migrated.
  //    * After the compensationWorker completes the compensation task, it is expected,
  //    * that the compensation catch event catches the token and runs the Undo task.
  //    *
  //    * When all these elements are selectable, it means that the compensation has been
  //    * migrated successfully.
  //    */
  //   await expect(
  //     await processInstancePage.diagram.getLabeledElement(
  //       'Compensation boundary event',
  //     ),
  //   ).toHaveClass(/op-selectable/, {timeout: 20000});
  //   await expect(
  //     await processInstancePage.diagram.getLabeledElement(
  //       'Compensation throw event',
  //     ),
  //   ).toHaveClass(/op-selectable/);
  //   await expect(
  //     processInstancePage.diagram.getFlowNode('Compensation task'),
  //   ).toHaveClass(/op-selectable/);
  //   await expect(processInstancePage.diagram.getFlowNode('Undo')).toHaveClass(
  //     /op-selectable/,
  //   );
  // });

  // test('Migrated message events', async ({
  //   operateProcessesPage,
  //   processInstancePage,
  //   page,
  // }) => {
  //   await operateProcessesPage.gotooperateProcessesPage({
  //     searchParams: {
  //       active: 'true',
  //       incidents: 'true',
  //       process: processV3.bpmnProcessId,
  //       version: processV3.version.toString(),
  //     },
  //   });

  //   await operateProcessesPage.getNthProcessInstanceLink(0).click();

  //   /**
  //    * Expect that the correlation key and message have been migrated from Task C to Task A2
  //    */
  //   await processInstancePage.diagram.clickFlowNode('Task A2');
  //   await processInstancePage.diagram.showMetaData();
  //   await page.waitForSelector('.monaco-aria-container'); // wait until monaco is fully loaded
  //   await expect(
  //     processInstancePage.metadataModal.getByText(
  //       '"correlationKey": "mySecondCorrelationKey"',
  //     ),
  //   ).toBeVisible();
  //   await expect(
  //     processInstancePage.metadataModal.getByText(
  //       '"messageName": "Message_2",',
  //     ),
  //   ).toBeVisible();

  //   await processInstancePage.metadataModal
  //     .getByRole('button', {name: /close/i})
  //     .click();

  //   await processInstancePage.diagram.clickFlowNode('Task A2'); // deselect Task A2

  //   /**
  //    * Expect that the correlation key and message have been migrated from Task A to Task C2
  //    */
  //   await processInstancePage.diagram.clickFlowNode('Task C2');
  //   await processInstancePage.diagram.showMetaData();
  //   await expect(
  //     processInstancePage.metadataModal.getByText(
  //       '"correlationKey": "myFirstCorrelationKey"',
  //     ),
  //   ).toBeVisible();
  //   await expect(
  //     processInstancePage.metadataModal.getByText(
  //       '"messageName": "Message_1",',
  //     ),
  //   ).toBeVisible();

  //   await processInstancePage.metadataModal
  //     .getByRole('button', {name: /close/i})
  //     .click();

  //   await processInstancePage.diagram.clickFlowNode('Task C2'); // deselect Task C2

  //   /**
  //    * Expect that the correlation key and message have been migrated from Message receive task to Message receive task 2
  //    */
  //   await processInstancePage.diagram.clickFlowNode('Message receive task 2');
  //   await processInstancePage.diagram.showMetaData();
  //   await page.waitForTimeout(500); // wait until metadata modal is fully rendered
  //   await expect(
  //     processInstancePage.metadataModal.getByText(
  //       '"correlationKey": "myFirstCorrelationKey"',
  //     ),
  //   ).toBeVisible();
  //   await expect(
  //     processInstancePage.metadataModal.getByText(
  //       '"messageName": "Message_5",',
  //     ),
  //   ).toBeVisible();

  //   await processInstancePage.metadataModal
  //     .getByRole('button', {name: /close/i})
  //     .click();

  //   await processInstancePage.diagram.clickFlowNode('Message receive task 2'); // deselect task

  //   /**
  //    * Expect that the correlation key has been updated if source and target message event have the same message id
  //    */
  //   await processInstancePage.diagram.clickEvent('Message intermediate catch2');
  //   await processInstancePage.diagram.showMetaData();
  //   await expect(
  //     processInstancePage.metadataModal.getByText(
  //       '"correlationKey": "myFirstCorrelationKey"',
  //     ),
  //   ).toBeVisible();
  //   await expect(
  //     processInstancePage.metadataModal.getByText(
  //       '"messageName": "Message_3",',
  //     ),
  //   ).toBeVisible();
  //   await page.waitForTimeout(500); // wait until metadata modal is fully rendered
  //   await processInstancePage.metadataModal
  //     .getByRole('button', {name: /close/i})
  //     .click();
  // });

  // test('Migrated gateways', async ({
  //   operateProcessesPage,
  //   processInstancePage,
  //   page,
  // }) => {
  //   await operateProcessesPage.gotooperateProcessesPage({
  //     searchParams: {
  //       active: 'true',
  //       incidents: 'true',
  //       process: processV3.bpmnProcessId,

  //       version: processV3.version.toString(),
  //     },
  //   });

  //   await operateProcessesPage.getNthProcessInstanceLink(0).click();

  //   await processInstancePage.diagram.resetDiagramZoomButton.click();

  //   /**
  //    * Expect that the correlation key on the event based gateway has been updated
  //    */
  //   await processInstancePage.diagram.clickGateway('Event based gateway 2');
  //   await processInstancePage.diagram.showMetaData();
  //   await expect(
  //     processInstancePage.metadataModal.getByText(
  //       '"correlationKey": "myFirstCorrelationKey"',
  //     ),
  //   ).toBeVisible();
  //   await expect(
  //     processInstancePage.metadataModal.getByText(
  //       '"messageName": "Message_3",',
  //     ),
  //   ).toBeVisible();
  //   await page.waitForTimeout(500); // wait until metadata modal is fully rendered
  //   await processInstancePage.metadataModal
  //     .getByRole('button', {name: /close/i})
  //     .click();

  //   /**
  //    * Expect that the incident on exclusive gateway has been migrated
  //    */
  //   await processInstancePage.diagram.clickGateway('Exclusive gateway 2');
  //   await expect(
  //     processInstancePage.diagram.popover.getByRole('heading', {
  //       name: 'Incident',
  //     }),
  //   ).toBeVisible();
  // });

  // test('Migrated date tag', async ({
  //   operateProcessesPage,
  //   processInstancePage,
  // }) => {
  //   const targetBpmnProcessId = processV3.bpmnProcessId;
  //   const targetVersion = processV3.version.toString();

  //   await operateProcessesPage.gotooperateProcessesPage({
  //     searchParams: {
  //       active: 'true',
  //       incidents: 'true',
  //       process: targetBpmnProcessId,
  //       version: targetVersion,
  //     },
  //   });

  //   // Navigate to the first process instance in the list, that has been migrated
  //   await operateProcessesPage.getNthProcessInstanceLink(0).click();

  //   // Expect the migrated tag to be visible
  //   await expect(
  //     processInstancePage.instanceHistory.getByText(/^migrated/i),
  //   ).toBeVisible();
  // });

  // test('Migrated signal elements', async ({
  //   operateProcessesPage,
  //   processInstancePage,
  //   request,
  // }) => {
  //   const targetBpmnProcessId = processV3.bpmnProcessId;
  //   const targetVersion = processV3.version.toString();

  //   await zeebeGrpcApi.broadcastSignal('Signal_2');

  //   // Wait until all signals have been received
  //   await expect
  //     .poll(
  //       async () => {
  //         const response = await request.post(
  //           `${config.endpoint}/v1/flownode-instances/search`,
  //           {
  //             data: {
  //               filter: {
  //                 flowNodeId: 'SignalIntermediateCatch2',
  //                 state: 'COMPLETED',
  //                 processDefinitionKey: processV3.processDefinitionKey,
  //               },
  //             },
  //           },
  //         );

  //         return await response.json();
  //       },
  //       {timeout: SETUP_WAITING_TIME},
  //     )
  //     .toHaveProperty('total', 3);

  //   await operateProcessesPage.gotooperateProcessesPage({
  //     searchParams: {
  //       active: 'true',
  //       incidents: 'true',
  //       process: targetBpmnProcessId,
  //       version: targetVersion,
  //     },
  //   });

  //   // Navigate to the first process instance in the list, that has been migrated
  //   await operateProcessesPage.getNthProcessInstanceLink(0).click();

  //   await processInstancePage.diagram.resetDiagramZoomButton.click();

  //   /**
  //   /* Expect that signal intermediate catch event has received Signal_2,
  //   /* which has been migrated from orderProcessMigration version 1 to version 3
  //    */
  //   await processInstancePage.diagram.clickEvent('Signal intermediate catch2');
  //   await processInstancePage.diagram.showMetaData();

  //   await expect(
  //     processInstancePage.metadataModal.getByText('endDate'),
  //   ).toBeVisible();
  //   await expect(
  //     processInstancePage.metadataModal.getByText('"endDate": "null"'),
  //   ).not.toBeVisible();

  //   await processInstancePage.metadataModal
  //     .getByRole('button', {name: /close/i})
  //     .click();
  // });

  // test('Migrated multi instance elements', async ({
  //   operateProcessesPage,
  //   processInstancePage,
  // }) => {
  //   const targetBpmnProcessId = processV3.bpmnProcessId;
  //   const targetVersion = processV3.version.toString();

  //   await operateProcessesPage.gotooperateProcessesPage({
  //     searchParams: {
  //       active: 'true',
  //       incidents: 'true',
  //       process: targetBpmnProcessId,
  //       version: targetVersion,
  //     },
  //   });

  //   // Navigate to the first process instance in the list, that has been migrated
  //   await operateProcessesPage.getNthProcessInstanceLink(0).click();

  //   await processInstancePage.diagram.resetDiagramZoomButton.click();

  //   await processInstancePage.diagram.clickSubProcess(
  //     'Multi instance sub process 2',
  //   );
  //   await processInstancePage.diagram.showMetaData();

  //   /**
  //   /* Expect that 1 instance of the multi instance sub process has been migrated
  //    */
  //   await expect(
  //     processInstancePage.metadataModal.getByText('endDate'),
  //   ).toBeVisible();
  //   await expect(
  //     processInstancePage.metadataModal.getByText('"endDate": "null"'),
  //   ).not.toBeVisible();

  //   await processInstancePage.metadataModal
  //     .getByRole('button', {name: /close/i})
  //     .click();

  //   await processInstancePage.diagram.clickSubProcess(
  //     'Multi instance sub process 2',
  //   ); // deselect sub process

  //   await processInstancePage.diagram.clickFlowNode('Multi instance task 2');

  //   /**
  //   /* Expect that 2 instances of the multi instance task have been migrated
  //    */
  //   await expect(
  //     processInstancePage.diagram.popover.getByText(
  //       'This Flow Node triggered 2 times',
  //     ),
  //   ).toBeVisible();
  // });
});
