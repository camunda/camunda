/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {deploy, createInstances} from 'utils/zeebeClient';
import {sleep} from 'utils/sleep';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';

test.beforeAll(async ({resetData}) => {
  await resetData();
  await deploy([
    './resources/usertask_to_be_assigned.bpmn',
    './resources/usertask_for_scrolling_1.bpmn',
    './resources/usertask_for_scrolling_2.bpmn',
    './resources/usertask_for_scrolling_3.bpmn',
  ]);
  await sleep(1000);

  await createInstances('usertask_for_scrolling_3', 1, 1);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_1', 1, 1);
  await createInstances('usertask_to_be_assigned', 1, 1); // this task will be seen on top since it is created last

});

// Serial mode ensures filter selection → update task list → scrolling execute in order so that
// usertask_to_be_assigned is completed before the scrolling test runs.
test.describe.serial('task panel page', () => {
  test.beforeEach(async ({page, taskListLoginPage}) => {
    await navigateToApp(page, 'tasklist');
    await taskListLoginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('filter selection', async ({taskPanelPage}) => {
    test.slow();
    await expect(
      taskPanelPage.availableTasks.getByText('Some user activity'),
    ).toHaveCount(50);

    await taskPanelPage.filterBy('Assigned to me');
    await expect(taskPanelPage.availableTasks).toContainText('No tasks found');

    await taskPanelPage.filterBy('All open tasks');
    await expect(
      taskPanelPage.availableTasks.getByText('Some user activity'),
    ).toHaveCount(50);
    await expect(
      taskPanelPage.availableTasks.getByText('No tasks found'),
    ).toHaveCount(0);
  });

  test('update task list according to user actions', async ({
    page,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('usertask_to_be_assigned');

    await expect(taskDetailsPage.emptyTaskMessage).toBeVisible();
    await taskDetailsPage.clickAssignToMeButton();
    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await page.reload();

    await expect(async () => {
      await expect(
        taskPanelPage.availableTasks.getByText('usertask_to_be_assigned'),
      ).toHaveCount(0);
    }).toPass({timeout: 5000});

    await taskPanelPage.filterBy('Assigned to me');
    await taskPanelPage.openTask('usertask_to_be_assigned');

    await expect(taskDetailsPage.completeTaskButton).toBeEnabled();
    await taskDetailsPage.clickCompleteTaskButton();

    await expect(async () => {
      await expect(taskPanelPage.availableTasks.getByText('user')).toHaveCount(
        0,
      );
    }).toPass({timeout: 5000});

    await taskPanelPage.filterBy('Completed');

    await expect(async () => {
      await expect(
        taskPanelPage.availableTasks.getByText(/some text/),
      ).not.toHaveCount(50);
    }).toPass({timeout: 5000});
  });

  test('scrolling', async ({page, taskPanelPage}) => {
    test.slow();

    // The initial page shows 50 tasks; polling up to 60 s acts as a conditional wait for
    // the first-page counts to stabilise after beforeAll creates 200+ instances.
    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1, {timeout: 60000});
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(49, {timeout: 60000});
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0, {timeout: 60000});

    await taskPanelPage.scrollToLastTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(99);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await taskPanelPage.scrollToLastTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(149);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await taskPanelPage.scrollToLastTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(199);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await taskPanelPage.scrollToLastTask('usertask_for_scrolling_2');

    // The virtual window drops items from the top when the end of the list loads.
    // Assert only the meaningful boundary conditions: scrolling_1 scrolls off the top
    // and scrolling_3 becomes visible at the bottom.
    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(0, {timeout: 15000});
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(1, {timeout: 15000});

    await taskPanelPage.scrollToFirstTask('usertask_for_scrolling_2');

    // After scrolling back to the top the boundary conditions reverse.
    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1, {timeout: 15000});
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0, {timeout: 15000});
  });
});
