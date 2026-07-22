/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {publicTest as test} from 'fixtures';
import {deploy, cancelProcessInstance} from 'utils/zeebeClient';
import {jsonHeaders} from 'utils/http';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {waitForAssertion} from 'utils/waitForAssertion';
import {clearAllProcessInstances} from '@requestHelpers';
import {uniqueBusinessId} from 'utils/constants';

const USER_TASK_PROCESS_ID = 'user_task_api_test_process';

const BUSINESS_ID = uniqueBusinessId('ui-task-bizid');

let instanceKey: string | undefined;

async function startInstanceWithBusinessId(
  request: import('@playwright/test').APIRequestContext,
  businessId: string,
): Promise<string> {
  const res = await request.post('/v2/process-instances', {
    headers: jsonHeaders(),
    data: {processDefinitionId: USER_TASK_PROCESS_ID, businessId},
  });
  if (res.status() !== 200) {
    console.error('Unexpected status code:', res.status(), await res.text());
  }
  expect(res.status()).toBe(200);
  const json = await res.json();
  expect(json.processInstanceKey).toBeTruthy();
  return String(json.processInstanceKey);
}

test.beforeAll(async ({request}) => {
  await clearAllProcessInstances(request);
  await deploy(['./resources/user_task_api_test_process.bpmn']);
  instanceKey = await startInstanceWithBusinessId(request, BUSINESS_ID);
});

test.afterAll(async () => {
  const keys = [instanceKey].filter((key): key is string => Boolean(key));
  await Promise.allSettled(
    keys.map(async (key) => {
      try {
        await cancelProcessInstance(key);
      } catch (error) {
        console.warn(`Failed to cancel process instance ${key}:`, error);
      }
    }),
  );
});

test.describe('Tasklist - Business ID', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Task list shows the Business ID', async ({page, taskPanelPage}) => {
    await test.step('Verify the task card shows the Business ID', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(
            taskPanelPage.taskCards.filter({hasText: BUSINESS_ID}),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });
  });

  test('Task detail shows the Business ID', async ({
    page,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    const taskCard = taskPanelPage.taskCards.filter({hasText: BUSINESS_ID});

    await test.step('Locate the task card by Business ID', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(taskCard).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Open the task detail', async () => {
      await taskCard.click();
    });

    await test.step('Verify the detail panel shows the Business ID label and value', async () => {
      await expect(taskDetailsPage.detailsPanel).toBeVisible();
      await expect(
        taskDetailsPage.detailsPanel.getByText('Business ID', {exact: true}),
      ).toBeVisible();
      await expect(
        taskDetailsPage.detailsPanel.getByText(BUSINESS_ID),
      ).toBeVisible();
    });
  });
});
