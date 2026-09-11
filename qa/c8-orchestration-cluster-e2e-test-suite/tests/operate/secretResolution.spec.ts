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
import {navigateToAppHome} from '@pages/UtilitiesPage';

// Matches config/secrets/MY_SECRET, mounted into the broker at
// /etc/camunda/secrets by config/docker-compose.yml.
const SECRET_VALUE = 'dummy-value-for-e2e-not-a-real-secret';
const PLACEHOLDER = 'camunda.secrets.MY_SECRET';

type ProcessInstance = {processInstanceKey: string};

let processInstance: ProcessInstance;
let workerVariables: Record<string, unknown> | undefined;

test.beforeAll(async () => {
  await deploy(['./resources/secretResolutionProcess.bpmn']);

  const worker = createWorker(
    'secretConsumerTask',
    false,
    {},
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (job: any) => {
      workerVariables = job.variables;
      return job.complete();
    },
  );

  processInstance = await createSingleInstance('secretResolutionProcess', 1);

  // Secret resolution is requested from the job activation path rather than at
  // job creation, so the first poll registers the request and a later poll
  // collects the job. The engine's idle scheduler interval is 5s, so this wait
  // needs to stay generous - do not tighten it.
  await expect(async () => {
    expect(workerVariables).toBeDefined();
  }).toPass({timeout: 30000});

  await worker.close();
});

test.describe('Secret resolution', () => {
  test.beforeEach(async ({page, operateHomePage}) => {
    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('worker gets the value, Operate shows the placeholder', async ({
    operateProcessInstancePage,
  }) => {
    await test.step('worker received the resolved secret', async () => {
      expect(workerVariables?.token).toBe(SECRET_VALUE);
    });

    await test.step('open the instance and select the service task', async () => {
      await expect(async () => {
        await operateProcessInstancePage.gotoProcessInstancePage({
          id: processInstance.processInstanceKey,
        });
        await expect(operateProcessInstancePage.instanceHeader).toBeVisible({
          timeout: 15000,
        });
      }).toPass({timeout: 90000});

      // The input mapping creates a variable local to the element instance, so
      // it is not listed at root process scope. Selecting the task is required.
      await operateProcessInstancePage.clickTreeItem(/consume secret/i);
      await operateProcessInstancePage.variablesTabButton.click();
      await expect(operateProcessInstancePage.variablesList).toBeVisible({
        timeout: 60000,
      });
    });

    await test.step('stored variable holds the placeholder', async () => {
      const variable =
        operateProcessInstancePage.existingVariableByName('token');

      // Both assertions are needed. On its own the first still passes if
      // resolution stops entirely and the placeholder is handed straight to the
      // worker; the second still passes if resolved values start being
      // persisted alongside it.
      await expect(variable.value).toHaveText(`"${PLACEHOLDER}"`);
      await expect(variable.value).not.toContainText(SECRET_VALUE);
    });
  });
});
