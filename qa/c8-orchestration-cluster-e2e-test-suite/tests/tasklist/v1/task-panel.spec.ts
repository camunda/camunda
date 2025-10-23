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

  test('filter selection', async ({taskPanelPageV1}) => {
    test.slow();
    await expect(
      taskPanelPageV1.availableTasks.getByText('Some user activity'),
    ).toHaveCount(50);

    await taskPanelPageV1.filterBy('Assigned to me');
    await expect(taskPanelPageV1.availableTasks).toContainText(
      'No tasks found',
    );

    await taskPanelPageV1.filterBy('All open tasks');
    await expect(
      taskPanelPageV1.availableTasks.getByText('Some user activity'),
    ).toHaveCount(50);
    await expect(
      taskPanelPageV1.availableTasks.getByText('No tasks found'),
    ).toHaveCount(0);
  });

  test('update task list according to user actions', async ({
    page,
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.filterBy('Unassigned');
    await expect(async () => {
      await expect(
        taskPanelPageV1.availableTasks.getByText('usertask_to_be_assigned'),
      ).toBeVisible();
    }).toPass();
    await taskPanelPageV1.openTask('usertask_to_be_assigned');
    await expect(taskDetailsPageV1.emptyTaskMessage).toBeVisible();
    await taskDetailsPageV1.clickAssignToMeButton();
    await expect(taskDetailsPageV1.unassignButton).toBeVisible();
    await page.reload();

    await expect(async () => {
      await expect(
        taskPanelPageV1.availableTasks.getByText('usertask_to_be_assigned'),
      ).toHaveCount(0);
    }).toPass({timeout: 5000});

    await taskPanelPageV1.filterBy('Assigned to me');
    await taskPanelPageV1.openTask('usertask_to_be_assigned');

    await expect(taskDetailsPageV1.completeTaskButton).toBeEnabled();
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await expect(async () => {
      await expect(
        taskPanelPageV1.availableTasks.getByText('user'),
      ).toHaveCount(0);
    }).toPass();

    await taskPanelPageV1.filterBy('Completed');

    await expect(async () => {
      await expect(
        taskPanelPageV1.availableTasks.getByText(/some text/),
      ).not.toHaveCount(50);
    }).toPass({timeout: 5000});
  });

  test('scrolling', async ({page, taskPanelPageV1}) => {
    test.slow();

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(49);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await taskPanelPageV1.scrollToLastTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(99);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await taskPanelPageV1.scrollToLastTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(149);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await taskPanelPageV1.scrollToLastTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(199);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await taskPanelPageV1.scrollToLastTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(0);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(199);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(1);

    await taskPanelPageV1.scrollToFirstTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(199);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);
  });
});
