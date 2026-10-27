/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {publicTest as test} from 'fixtures';
import {deploy, createInstances} from 'utils/zeebeClient';
import {sleep} from 'utils/sleep';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {clearAllProcessInstances} from '@requestHelpers';

test.beforeAll(async ({request}) => {
  await clearAllProcessInstances(request);

  await deploy([
    './resources/usertask_to_be_assigned.bpmn',
    './resources/usertask_for_scrolling_1.bpmn',
    './resources/usertask_for_scrolling_2.bpmn',
    './resources/usertask_for_scrolling_3.bpmn',
  ]);
  await sleep(100);

  await createInstances('usertask_for_scrolling_3', 1, 1);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_1', 1, 1);
  await createInstances('usertask_to_be_assigned', 1, 1); // this task will be seen on top since it is created last

  await sleep(500);
});

test.describe('task panel page', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('filter selection', async ({taskPanelPage}) => {
    test.slow();
    // AvailableTasks.tsx now renders the list through @tanstack/react-virtual
    // (useVirtualizer), which only mounts DOM nodes for the rows within the
    // scroll container's viewport plus a small overscan -- unlike the
    // pre-migration list, which rendered every fetched row. All 50 fetched
    // tasks are still there (confirmed via the query's page size,
    // DEFAULT_MAX_ITEM_PER_PAGE = 50), but only a window of them (observed:
    // ~10 at the suite's default viewport) is ever actually in the DOM at
    // once, so asserting an exact toHaveCount(50) is asserting an
    // implementation detail of virtualization, not the filter behavior this
    // test is about. Assert presence/absence instead.
    await expect(
      taskPanelPage.availableTasks.getByText('Some user activity').first(),
    ).toBeVisible();

    await taskPanelPage.filterBy('Assigned to me');
    await expect(taskPanelPage.availableTasks).toContainText('No tasks found');

    await taskPanelPage.filterBy('All open tasks');
    await expect(
      taskPanelPage.availableTasks.getByText('Some user activity').first(),
    ).toBeVisible();
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
    await expect(async () => {
      await expect(
        taskPanelPage.availableTasks.getByText('usertask_to_be_assigned'),
      ).toBeVisible();
    }).toPass();
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
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await expect(async () => {
      await expect(taskPanelPage.availableTasks.getByText('user')).toHaveCount(
        0,
      );
    }).toPass();

    await taskPanelPage.filterBy('Completed');

    await expect(async () => {
      await expect(
        taskPanelPage.availableTasks.getByText(/some text/),
      ).not.toHaveCount(50);
    }).toPass({timeout: 5000});
  });

  // Skipped due to bug #62704: https://github.com/camunda/camunda/issues/62704
  // V2 mode may have different scrolling/pagination behavior that affects task count expectations
  test.skip('scrolling', async ({page, taskPanelPage}) => {
    test.slow();

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(49);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

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

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(0);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(199);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(1);

    await taskPanelPage.scrollToFirstTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(199);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);
  });
});
