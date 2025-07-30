/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, expect} from '@playwright/test';
import {TaskPanelPage} from '@pages/TaskPanelPage';
import {TaskDetailsPage} from '@pages/TaskDetailsPage';
import {sleep} from '../utils/sleep';

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
  } else if (appName == 'identity') {
    await page.goto(
      process.env.CORE_APPLICATION_URL + '/' + appName.toLowerCase() + '/login',
    );
  }
}

export async function validateURL(page: Page, URL: RegExp): Promise<void> {
  // eslint-disable-next-line @typescript-eslint/no-floating-promises, playwright/missing-playwright-await
  expect(page).toHaveURL(URL);
}

export async function completeTaskWithRetry(
  taskPanelPage: TaskPanelPage,
  taskDetailsPage: TaskDetailsPage,
  taskName: string,
  taskPriority: string,
  maxRetries: number = 3,
): Promise<void> {
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      await taskPanelPage.openTask(taskName);
      await sleep(500);
      if (!(await taskDetailsPage.assignedToMeText.isVisible())) {
        await taskDetailsPage.clickAssignToMeButton();
      }
      await expect(
        taskDetailsPage.detailsPanel.getByText(taskPriority),
      ).toBeVisible();
      await taskDetailsPage.clickCompleteTaskButton();
      await expect(
        taskPanelPage.availableTasks.getByText(taskName, {exact: true}).first(),
      ).not.toBeVisible({timeout: 5000});
      return;
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
