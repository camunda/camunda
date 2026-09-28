/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {randomUUID} from 'crypto';
import {
  cancelProcessInstance,
  createSingleInstance,
  createWorker,
  deployWithSubstitutions,
} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToAppHome} from '@pages/UtilitiesPage';

// Matches config/secrets/MY_SECRET, mounted into the broker at
// /etc/camunda/secrets by config/docker-compose.yml.
const SECRET_VALUE = 'dummy-value-for-e2e-not-a-real-secret';
const PLACEHOLDER = 'camunda.secrets.MY_SECRET';

// The process id and job type are per-run so that several Playwright projects
// sharing one cluster cannot activate each other's jobs. The secret reference
// stays fixed - the mounted store is read-only and shared by design.
const suffix = randomUUID().slice(0, 8);
const processId = `secretResolutionProcess-${suffix}`;
const jobType = `secretConsumerTask-${suffix}`;

let processInstanceKey: string;
let workerVariables: Record<string, unknown> | undefined;
const workers: Array<{close: () => Promise<unknown> | unknown}> = [];

test.beforeAll(async () => {
  await deployWithSubstitutions('./resources/secretResolutionProcess.bpmn', {
    secretResolutionProcess: processId,
    secretConsumerTask: jobType,
  });

  workers.push(
    createWorker(
      jobType,
      false,
      {},
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (job: any) => {
        workerVariables = job.variables;
        return job.complete();
      },
    ),
  );

  const instance = await createSingleInstance(processId, 1);
  processInstanceKey = String(instance.processInstanceKey);

  // Secret resolution is requested from the job activation path rather than at
  // job creation, so the first poll registers the request and a later poll
  // collects the job. The engine's idle scheduler interval is 5s, so this wait
  // needs to stay generous - do not tighten it.
  await expect(async () => {
    expect(workerVariables).toBeDefined();
  }).toPass({timeout: 30000});
});

test.afterAll(async () => {
  // Runs even when the wait above throws, so a regression in secret resolution
  // fails this spec only instead of leaving a worker polling for the rest of
  // the suite.
  if (processInstanceKey) {
    await cancelProcessInstance(processInstanceKey);
  }
  for (const w of workers) {
    try {
      await w.close();
    } catch {
      // best-effort cleanup
    }
  }
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
          id: processInstanceKey,
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
