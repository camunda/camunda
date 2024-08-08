/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {deploy, createInstances} from '@/utils/zeebeClient';
import {test} from '@/test-fixtures';

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

test.beforeEach(async ({page, loginPage}) => {
  await loginPage.goto();
  await loginPage.login({
    username: 'demo',
    password: 'demo',
  });
  await expect(page).toHaveURL('/tasklist');
});

test.describe('task panel page', () => {
  test('filter selection', async ({page, tasksPage}) => {
    test.slow();
    await expect(
      tasksPage.availableTasks.getByText('Some user activity'),
    ).toHaveCount(50, {
      timeout: 10000,
    });

    await tasksPage.filterBy('Assigned to me');
    await expect(page).toHaveURL(/\?filter=assigned-to-me/);

    await page.reload();

    await expect(tasksPage.availableTasks).toContainText('No tasks found');

    await tasksPage.filterBy('All open tasks');
    await expect(page).toHaveURL(/\?filter=all-open/);

    await page.reload();

    await expect(page).toHaveURL(/\?filter=all-open/);
    await expect(
      tasksPage.availableTasks.getByText('Some user activity'),
    ).toHaveCount(50, {
      timeout: 10000,
    });

    await expect(
      tasksPage.availableTasks.getByText('No tasks found'),
    ).toHaveCount(0, {
      timeout: 10000,
    });
  });

  test('update task list according to user actions', async ({
    page,
    tasksPage,
  }) => {
    await tasksPage.filterBy('Unassigned');
    await expect(page).toHaveURL(/\?filter=unassigned/);
    await tasksPage.openTask('usertask_to_be_assigned');

    await expect(tasksPage.emptyTaskMessage).toBeVisible();
    await tasksPage.assignToMeButton.click();
    await expect(tasksPage.unassignButton).toBeVisible();
    await page.reload();

    await expect(
      tasksPage.availableTasks.getByText('usertask_to_be_assigned'),
    ).toHaveCount(0);

    await tasksPage.filterBy('Assigned to me');
    await expect(page).toHaveURL(/\?filter=assigned-to-me/);
    await tasksPage.openTask('usertask_to_be_assigned');

    await expect(tasksPage.completeTaskButton).toBeEnabled();
    await tasksPage.completeTaskButton.click();
    await page.reload();

    await expect(
      tasksPage.availableTasks.getByText('Some user activity'),
    ).toHaveCount(0);

    await tasksPage.filterBy('Completed');

    await expect(page).toHaveURL(/\?filter=completed/);
    await expect(page.getByText(/some text/)).not.toHaveCount(50);
  });

  test('scrolling', async ({page, tasksPage}) => {
    test.slow();

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(49);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await tasksPage.scrollToLastTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(99);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await tasksPage.scrollToLastTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(149);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await tasksPage.scrollToLastTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(199);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await tasksPage.scrollToLastTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(0);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(199);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(1);

    await tasksPage.scrollToFirstTask('usertask_for_scrolling_2');

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(199);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);
  });
});
