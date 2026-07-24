/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {readFileSync} from 'node:fs';
import {test} from 'fixtures';
import {expect, request as playwrightRequest} from '@playwright/test';
import {encode} from 'utils/http';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {
  startIsolatedEnvironmentWaitStatesOff,
  stopIsolatedEnvironment,
} from 'utils/dockerComposeControl';

const ISOLATED_BASE_URL =
  process.env.WAITSTATES_ISOLATED_BASE_URL ?? 'http://localhost:29080';
const authHeader = `Basic ${encode('demo:demo')}`;

test.describe.serial('Wait States Flag Off', () => {
  test.setTimeout(5 * 60 * 1000);

  test.beforeAll(async () => {
    await startIsolatedEnvironmentWaitStatesOff();

    const context = await playwrightRequest.newContext();
    try {
      await expect(async () => {
        const res = await context.get(`${ISOLATED_BASE_URL}/v2/topology`, {
          headers: {Authorization: authHeader},
        });
        expect(res.status()).toBe(200);
      }).toPass({intervals: [2_000, 5_000, 10_000], timeout: 120_000});
    } finally {
      await context.dispose();
    }
  });

  test.afterAll(async () => {
    await stopIsolatedEnvironment();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('no waiting indicators render and no wait-states/search request fires when the flag is off', async ({
    page,
  }) => {
    let processInstanceKey = '';

    await test.step('Deploy a process and start an instance that would otherwise wait on a job', async () => {
      const bpmn = readFileSync(
        './resources/simpleServiceTaskProcess.bpmn',
        'utf-8',
      );
      const deployRes = await page
        .context()
        .request.post(`${ISOLATED_BASE_URL}/v2/deployments`, {
          headers: {Authorization: authHeader},
          multipart: {
            resources: {
              name: 'simpleServiceTaskProcess.bpmn',
              mimeType: 'application/xml',
              buffer: Buffer.from(bpmn),
            },
          },
        });
      expect(deployRes.status()).toBe(200);

      const createRes = await page
        .context()
        .request.post(`${ISOLATED_BASE_URL}/v2/process-instances`, {
          headers: {
            Authorization: authHeader,
            'Content-Type': 'application/json',
          },
          data: {processDefinitionId: 'simpleServiceTaskProcess'},
        });
      expect(createRes.status()).toBe(200);
      processInstanceKey = (await createRes.json()).processInstanceKey;
    });

    const waitStatesRequests: string[] = [];
    page.on('request', (req) => {
      if (req.url().includes('/wait-states/search')) {
        waitStatesRequests.push(req.url());
      }
    });

    await test.step('Log in against the isolated stack', async () => {
      const loginRes = await page
        .context()
        .request.post(`${ISOLATED_BASE_URL}/login`, {
          form: {username: 'demo', password: 'demo'},
        });
      expect(loginRes.ok()).toBe(true);
    });

    await test.step('Open the process instance and confirm no waiting UI or query', async () => {
      await page.goto(
        `${ISOLATED_BASE_URL}/operate/processes/${processInstanceKey}`,
      );
      await expect(page.getByTestId('instance-header')).toBeVisible();

      await expect(page.getByTestId('waiting-state-overlay')).toBeHidden();

      await page.getByRole('treeitem', {name: 'task', exact: true}).click();
      const detailsTabButton = page
        .getByLabel('Process Instance Bottom Panel Tabs')
        .getByRole('link', {name: /^Details$/i});
      await detailsTabButton.click();
      await expect(page.getByTestId('instance-header')).toBeVisible();
      await expect(page.getByTestId('waiting-status')).toBeHidden();

      expect(waitStatesRequests).toHaveLength(0);
    });
  });
});
