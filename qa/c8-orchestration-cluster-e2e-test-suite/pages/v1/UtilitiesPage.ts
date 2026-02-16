/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, expect} from '@playwright/test';
import {waitForAssertion} from 'utils/waitForAssertion';
import {TaskPanelPageV1} from '@pages/v1/TaskPanelPage';
import {TaskDetailsPageV1} from '@pages/v1/TaskDetailsPage';
import {sleep} from 'utils/sleep';

export async function navigateToApp(
  page: Page,
  appName: string,
): Promise<void> {
  if (appName == 'operate') {
    await page.goto(
      process.env.CORE_APPLICATION_URL + '/' + appName.toLowerCase() + '/login',
    );
  } else if (appName == 'tasklist') {
    await page.goto(
      process.env.CORE_APPLICATION_URL + '/' + appName.toLowerCase() + '/login',
    );
  } else if (appName == 'admin') {
    await page.goto(
      process.env.CORE_APPLICATION_URL + '/admin/login',
    );
  }
}

export async function validateURL(page: Page, URL: RegExp): Promise<void> {
  // eslint-disable-next-line @typescript-eslint/no-floating-promises, playwright/missing-playwright-await
  expect(page).toHaveURL(URL);
}

export async function completeTaskWithRetryV1(
  taskPanelPageV1: TaskPanelPageV1,
  taskDetailsPageV1: TaskDetailsPageV1,
  taskName: string,
  taskPriority: string,
  maxRetries: number = 3,
): Promise<void> {
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      await taskPanelPageV1.openTask(taskName);
      await sleep(500);
      if (!(await taskDetailsPageV1.assignedToMeText.isVisible())) {
        await taskDetailsPageV1.clickAssignToMeButton();
      }
      await expect(
        taskDetailsPageV1.detailsPanel.getByText(taskPriority),
      ).toBeVisible();
      await taskDetailsPageV1.clickCompleteTaskButton();
      let assertionPassed = false;
      try {
        await waitForAssertion({
          assertion: async () => {
            await expect(
              taskPanelPageV1.availableTasks
                .getByText(taskName, {exact: true})
                .first(),
            ).not.toBeVisible({timeout: 10000});
          },
          onFailure: async () => {
            console.log(
              `Task ${taskName} still visible, retrying waitForAssertion...`,
            );
          },
          maxRetries: 4,
        });
        assertionPassed = true;
      } catch (error) {
        console.log(`waitForAssertion failed for task ${taskName}: ${error}`);
      }

      if (assertionPassed) {
        return;
      }
    } catch (error) {
      if (attempt < maxRetries - 1) {
        console.warn(
          `Attempt ${
            attempt + 1
          } failed for completing task ${taskName}. Retrying...`,
        );
      } else {
        console.error(error);
        throw new Error(`Assertion failed after ${maxRetries} attempts`);
      }
    }
  }
}
