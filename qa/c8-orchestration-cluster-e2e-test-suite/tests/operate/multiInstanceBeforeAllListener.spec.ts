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
import {navigateToApp} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';

// Epic camunda/camunda#49208 — UI verification in Operate
const suffix = randomUUID().slice(0, 8);
const processId = `mi-el-basic-collection-ui-${suffix}`;
const beforeAllJobType = `mi-body-init-collection-ui-${suffix}`;
const innerJobType = `mi-inner-worker-ui-${suffix}`;

let processInstanceKey: string;
const workers: Array<{close: () => Promise<unknown> | unknown}> = [];

test.beforeAll(async () => {
  await deployWithSubstitutions('./resources/mi-el-basic-collection.bpmn', {
    'mi-el-basic-collection': processId,
    'mi-body-init-collection': beforeAllJobType,
    'mi-inner-worker': innerJobType,
  });

  // Drive beforeAll only. Inner jobs intentionally left pending so the MI
  // body stays ACTIVE and the MI element is selectable in Operate.
  workers.push(
    createWorker(beforeAllJobType, false, {
      items: ['alpha', 'beta', 'gamma'],
    }),
  );

  const instance = await createSingleInstance(processId, 1);
  processInstanceKey = String(instance.processInstanceKey);

  // Allow beforeAll to fire and the MI body to materialise.
  await sleep(5000);
});

test.afterAll(async () => {
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

test.describe('Operate — Multi-Instance beforeAll listener visibility', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Operate shows beforeAll execution listener on MI task and variables produced by beforeAll', async ({
    operateProcessInstancePage,
  }) => {
    await test.step('Open the process instance and select the MI service task', async () => {
      await operateProcessInstancePage.gotoProcessInstancePage({
        id: processInstanceKey,
      });
      await operateProcessInstancePage.diagramHelper.clickFlowNode(
        'MI Service Task',
      );
    });

    await test.step('Listeners tab shows the beforeAll execution listener', async () => {
      await operateProcessInstancePage.openListenersTab();
      await expect(
        operateProcessInstancePage.getListenerRows('execution'),
      ).toHaveCount(1);
      await expect(
        operateProcessInstancePage.getExecutionListenerRowsByEventType(
          'beforeAll',
        ),
      ).toHaveCount(1);
    });

    await test.step('Variables panel exposes items produced by beforeAll', async () => {
      await operateProcessInstancePage.clickVariablesTab();
      const itemsVar =
        operateProcessInstancePage.existingVariableByName('items');
      await expect(itemsVar.name).toBeVisible();
      await expect(itemsVar.value).toContainText('alpha');
      await expect(itemsVar.value).toContainText('beta');
      await expect(itemsVar.value).toContainText('gamma');
    });
  });
});
