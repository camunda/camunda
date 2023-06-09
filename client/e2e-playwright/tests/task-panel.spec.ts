/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test, expect} from '@playwright/test';
import {deploy, createInstances} from '../zeebeClient';

test.beforeAll(async () => {
  await Promise.all([
    deploy('./e2e-playwright/resources/usertask_to_be_assigned.bpmn'),
    deploy('./e2e-playwright/resources/usertask_for_scrolling_1.bpmn'),
    deploy('./e2e-playwright/resources/usertask_for_scrolling_2.bpmn'),
    deploy('./e2e-playwright/resources/usertask_for_scrolling_3.bpmn'),
  ]);
  await createInstances('usertask_for_scrolling_3', 1, 1);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_1', 1, 1);
  await createInstances('usertask_to_be_assigned', 1, 1); // this task will be seen on top since it is created last
});

test.beforeEach(async ({page}) => {
  await page.goto('/login');
  await page.getByPlaceholder('Username').fill('demo');
  await page.getByPlaceholder('Password').fill('demo');
  await page.getByRole('button', {name: 'Login'}).click();
  await expect(page).toHaveURL('/');
});

test.describe('task panel page', () => {
  test('filter selection', async ({page}) => {
    await expect(
      page.getByTitle('Available tasks').getByText('Some user activity'),
    ).toHaveCount(50, {
      timeout: 10000,
    });

    await page.getByRole('combobox', {name: 'Filter options'}).click();
    await page
      .getByRole('region', {name: 'Filters'})
      .getByText('Assigned to me')
      .click();

    await expect(page).toHaveURL(/\?filter=assigned-to-me/);
    await page.reload();
    await expect(
      page.getByTitle('Available tasks').getByText('No tasks found'),
    ).toBeVisible();

    await page.getByRole('combobox', {name: /filter options/i}).click();
    await page.getByText('All open').click();

    await expect(page).toHaveURL(/\?filter=all-open/);

    await page.reload();

    await expect(page).toHaveURL(/\?filter=all-open/);
    await expect(
      page.getByTitle('Available tasks').getByText('Some user activity'),
    ).toHaveCount(50, {
      timeout: 10000,
    });

    await expect(
      page.getByTitle('Available tasks').getByText('No tasks found'),
    ).toHaveCount(0);
  });

  test('update task list according to user actions', async ({page}) => {
    await page.getByRole('combobox', {name: 'Filter options'}).click();
    await page
      .getByRole('option', {name: 'Unassigned'})
      .getByText('Unassigned')
      .click();

    await expect(page).toHaveURL(/\?filter=unassigned/);

    await page
      .getByTitle('Available tasks')
      .getByText('usertask_to_be_assigned')
      .click();
    await expect(
      page.getByRole('heading', {
        name: /task has no variables/i,
      }),
    ).toBeVisible();
    await page.getByRole('button', {name: 'Assign to me'}).click();
    await expect(page.getByRole('button', {name: 'Unassign'})).toBeVisible();
    await page.reload();
    await expect(
      page.getByTitle('Available tasks').getByText('usertask_to_be_assigned'),
    ).toHaveCount(0);

    await page.getByRole('combobox', {name: 'Filter options'}).click();
    await page
      .getByRole('region', {name: 'Filters'})
      .getByText('Assigned to me')
      .click();

    await expect(page).toHaveURL(/\?filter=assigned-to-me/);

    await page
      .getByTestId('scrollable-list')
      .getByRole('link', {name: 'Task assigned to me: Some user activity'})
      .first()
      .click();

    await expect(
      page.getByRole('button', {name: 'Complete Task'}),
    ).toBeVisible();
    expect(page.getByRole('button', {name: 'Complete Task'})).toBeEnabled();
    await page.getByRole('button', {name: 'Complete Task'}).click();
    await page.reload();
    await expect(
      page.getByTitle('Available tasks').getByText('Some user activity'),
    ).toHaveCount(0);

    await page.getByRole('combobox', {name: /filter options/i}).click();
    await page
      .getByRole('region', {name: 'Filters'})
      .getByText('Completed')
      .click();

    await expect(page).toHaveURL(/\?filter=completed/);
    await expect(page.getByText(/some text/)).not.toHaveCount(50);
  });

  test.skip('scrolling', async ({page}) => {
    test.setTimeout(40000);

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(49);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await page
      .getByText('usertask_for_scrolling_2')
      .last()
      .scrollIntoViewIfNeeded();

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(99);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await page
      .getByText('usertask_for_scrolling_2')
      .last()
      .scrollIntoViewIfNeeded();

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(149);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await page
      .getByText('usertask_for_scrolling_2')
      .last()
      .scrollIntoViewIfNeeded();

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(199);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);

    await page
      .getByText('usertask_for_scrolling_2')
      .last()
      .scrollIntoViewIfNeeded();

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(0);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(199);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(1);

    await page
      .getByText('usertask_for_scrolling_2')
      .first()
      .scrollIntoViewIfNeeded();

    await expect(page.getByText('usertask_for_scrolling_1')).toHaveCount(1);
    await expect(page.getByText('usertask_for_scrolling_2')).toHaveCount(199);
    await expect(page.getByText('usertask_for_scrolling_3')).toHaveCount(0);
  });
});
