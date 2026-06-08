/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createInstances} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';
import {waitForAssertion} from 'utils/waitForAssertion';
import {ProcessDeployment as ProcessDeploymentDTO} from '@camunda8/sdk/dist/c8/lib/C8Dto';

type ProcessDeployment = {
  readonly bpmnProcessId: string;
  readonly version: number;
  readonly processDefinitionKey: string;
};

type TestProcesses = {
  readonly parentProcess: ProcessDeployment;
  readonly childProcessV1: ProcessDeployment;
  readonly childProcessV2: ProcessDeployment;
  readonly parentProcessInstanceKeys: string[];
};

let testProcesses: TestProcesses;

test.beforeAll(async () => {
  // Deploy parent and child process v1
  const deployments = await deploy([
    './resources/callActivityParentProcess.bpmn',
    './resources/childProcess_v_1.bpmn',
  ]);

  const parentProcessVersion = deployments.processes.find(
    (process: ProcessDeploymentDTO) =>
      process.processDefinitionId === 'callActivityParentProcess',
  )?.processDefinitionVersion;

  const childProcessVersion = deployments.processes.find(
    (process: ProcessDeploymentDTO) =>
      process.processDefinitionId === 'childProcess',
  )?.processDefinitionVersion;

  const parentProcess: ProcessDeployment = {
    bpmnProcessId: 'callActivityParentProcess',
    version: parentProcessVersion ?? 1,
    processDefinitionKey: '',
  };

  const childProcessV1: ProcessDeployment = {
    bpmnProcessId: 'childProcess',
    version: childProcessVersion ?? 1,
    processDefinitionKey: '',
  };

  // The engine processes deployments asynchronously after the REST API returns,
  // so createProcessInstance can get a 404 (process definition not yet committed)
  // if called immediately after deploy. Retry with backoff only for that expected
  // "not found" case; rethrow any other error (auth/network/validation) immediately.
  // eslint-disable-next-line prefer-const
  let parentInstances: Awaited<ReturnType<typeof createInstances>> = [];
  for (let attempt = 1; attempt <= 5; attempt++) {
    try {
      parentInstances = await createInstances(
        parentProcess.bpmnProcessId,
        parentProcess.version,
        2,
      );
      break;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      const isNotFound = /404|NOT_FOUND/i.test(message);
      if (!isNotFound || attempt === 5) throw error;
      console.log(
        `createInstances attempt ${attempt} failed (process definition not yet committed), retrying after ${attempt * 2}s...`,
      );
      await sleep(attempt * 2000);
    }
  }

  const parentProcessInstanceKeys = parentInstances.map(
    (instance) => instance.processInstanceKey,
  );

  const secondDeployment = await deploy(['./resources/childProcess_v_2.bpmn']);
  const secondChildProcessVersion = secondDeployment.processes.find(
    (process: ProcessDeploymentDTO) =>
      process.processDefinitionId === 'childProcess',
  )?.processDefinitionVersion;

  const childProcessV2: ProcessDeployment = {
    bpmnProcessId: 'childProcess',
    version: secondChildProcessVersion ?? 2,
    processDefinitionKey: '',
  };
  expect(childProcessV2.version).toBe(childProcessV1.version + 1);

  testProcesses = {
    parentProcess,
    childProcessV1,
    childProcessV2,
    parentProcessInstanceKeys,
  };

  await sleep(4000);
});

test.describe('Child Process Instance Migration', () => {
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

  test('Migrate Child Process Instances', async ({
    page,
    operateFiltersPanelPage,
    operateProcessesPage,
    operateProcessInstancePage,
    operateProcessMigrationModePage,
  }) => {
    test.slow();
    const sourceVersion = testProcesses.childProcessV1.version.toString();
    const sourceBpmnProcessId = testProcesses.childProcessV1.bpmnProcessId;
    const targetVersion = testProcesses.childProcessV2.version.toString();
    const targetBpmnProcessId = testProcesses.childProcessV2.bpmnProcessId;
    const parentInstanceKey = testProcesses.parentProcessInstanceKeys[0]!;

    await test.step('Navigate to parent process instance and view called child processes', async () => {
      await waitForAssertion({
        assertion: async () => {
          await operateFiltersPanelPage.selectProcess(
            testProcesses.parentProcess.bpmnProcessId,
          );
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 3,
      });
      await operateFiltersPanelPage.selectVersion(
        testProcesses.parentProcess.version.toString(),
      );

      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('2 results')).toBeVisible({
            timeout: 30000,
          });
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      const instanceLink =
        operateProcessesPage.processInstanceLinkByKey(parentInstanceKey);

      await expect(instanceLink).toBeVisible({timeout: 15000});
      await instanceLink.click();

      await operateProcessInstancePage.clickViewAllCalledInstances();

      const childVersion = await operateProcessesPage.versionCell
        .first()
        .innerText();

      await expect(operateProcessesPage.parentInstanceIdCell).toBeVisible();

      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(childVersion);

      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('1 result')).toBeVisible({
            timeout: 30000,
          });
        },
        onFailure: async () => {
          await page.reload();
          await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
          await operateFiltersPanelPage.selectVersion(sourceVersion);
          await operateFiltersPanelPage.displayOptionalFilter(
            'Parent Process Instance Key',
          );
          await operateFiltersPanelPage.fillParentProcessInstanceKeyFilter(
            parentInstanceKey,
          );
        },
      });
    });

    await test.step('Select child process instance for migration', async () => {
      await operateProcessesPage.selectProcessInstances(1);

      await operateProcessesPage.startMigration();
    });

    await test.step('Configure target process, version and map flow nodes', async () => {
      await operateProcessMigrationModePage.targetProcessCombobox.click();
      await operateProcessMigrationModePage
        .getOptionByName(targetBpmnProcessId)
        .click();

      await operateProcessMigrationModePage.targetVersionDropdown.click();
      await operateProcessMigrationModePage
        .getOptionByName(targetVersion)
        .click();

      await operateProcessMigrationModePage.mapFlowNode('Task', 'New Task');

      await operateProcessMigrationModePage.completeProcessInstanceMigration();
      await sleep(200);
    });

    await test.step('Verify 1 instance migrated to target version', async () => {
      await operateFiltersPanelPage.selectVersion(targetVersion);

      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('1 result')).toBeVisible({
            timeout: 60000,
          });
          await expect(
            operateProcessesPage.getVersionCells(targetVersion),
          ).toHaveCount(1, {timeout: 60000});
        },
        onFailure: async () => {
          await page.reload();
        },
      });

      await expect(operateProcessesPage.parentInstanceIdCell).toBeVisible();
    });

    await test.step('Verify remaining instances still at source version', async () => {
      await operateFiltersPanelPage.selectProcess(sourceBpmnProcessId);
      await operateFiltersPanelPage.selectVersion(sourceVersion);

      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('1 result')).toBeVisible({
            timeout: 30000,
          });
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });
  });
});
