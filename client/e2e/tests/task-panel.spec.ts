/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {deploy, createInstances} from '../zeebeClient';
import {test} from '../test-fixtures';

test.beforeAll(async () => {
  await Promise.all([
    deploy('./e2e/resources/usertask_to_be_assigned.bpmn'),
    deploy('./e2e/resources/usertask_for_scrolling_1.bpmn'),
    deploy('./e2e/resources/usertask_for_scrolling_2.bpmn'),
    deploy('./e2e/resources/usertask_for_scrolling_3.bpmn'),
  ]);
  await createInstances('usertask_for_scrolling_3', 1, 1);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_1', 1, 1);
  await createInstances('usertask_to_be_assigned', 1, 1); // this task will be seen on top since it is created last
});

test.afterAll(async ({resetData}) => {
  await resetData();
});

test.beforeEach(async ({page, testSetupPage, loginPage}) => {
  await testSetupPage.goToLoginPage();
  await loginPage.login({
    username: 'demo',
    password: 'demo',
  });
  await expect(page).toHaveURL('/');
});

test.describe('task panel page', () => {
  test('filter selection', async ({page, taskPanelPage}) => {
    await expect(
      taskPanelPage.availableTasks.getByText('Some user activity'),
    ).toHaveCount(50, {
      timeout: 10000,
    });

    await taskPanelPage.filterBy('Assigned to me');
    await expect(page).toHaveURL(/\?filter=assigned-to-me/);

    await page.reload();

    await expect(taskPanelPage.availableTasks).toContainText('No tasks found');

    await taskPanelPage.filterBy('All open');
    await expect(page).toHaveURL(/\?filter=all-open/);

    await page.reload();

    await expect(page).toHaveURL(/\?filter=all-open/);
    await expect(
      taskPanelPage.availableTasks.getByText('Some user activity'),
    ).toHaveCount(50, {
      timeout: 10000,
    });

    await expect(
      taskPanelPage.availableTasks.getByText('No tasks found'),
    ).toHaveCount(0, {
      timeout: 10000,
    });
  });

  test('update task list according to user actions', async ({
    page,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await expect(page).toHaveURL(/\?filter=unassigned/);
    await taskPanelPage.openTask('usertask_to_be_assigned');

    await expect(taskDetailsPage.emptyTaskMessage).toBeVisible();
    await taskDetailsPage.assignToMeButton.click();
    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await page.reload();

    await expect(
      taskPanelPage.availableTasks.getByText('usertask_to_be_assigned'),
    ).toHaveCount(0);

    await taskPanelPage.filterBy('Assigned to me');
    await expect(page).toHaveURL(/\?filter=assigned-to-me/);
    await taskPanelPage.openTask('usertask_to_be_assigned');

    await expect(taskDetailsPage.completeTaskButton).toBeEnabled();
    await taskDetailsPage.completeButton.click();
    await page.reload();

    await expect(
      taskPanelPage.availableTasks.getByText('Some user activity'),
    ).toHaveCount(0);

    await taskPanelPage.filterBy('Completed');

    await expect(page).toHaveURL(/\?filter=completed/);
    await expect(page.getByText(/some text/)).not.toHaveCount(50);
  });

  test.skip('scrolling', async ({page, taskPanelPage}) => {
    test.setTimeout(40000);

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
