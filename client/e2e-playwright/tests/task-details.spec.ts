/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test, expect} from '@playwright/test';
import {deploy, createInstances} from '../zeebeClient';

test.beforeAll(async () => {
  await deploy([
    './e2e-playwright/resources/usertask_to_be_completed.bpmn',
    './e2e-playwright/resources/user_task_with_form.bpmn',
    './e2e-playwright/resources/user_task_with_form_and_vars.bpmn',
    './e2e-playwright/resources/user_task_with_form_rerender_1.bpmn',
    './e2e-playwright/resources/user_task_with_form_rerender_2.bpmn',
  ]);
  await Promise.all([
    createInstances('usertask_to_be_completed', 1, 1),
    createInstances('user_registration', 1, 2),
    createInstances('user_registration_with_vars', 1, 1, {
      name: 'Jane',
      age: '50',
    }),
    createInstances('user_task_with_form_rerender_1', 1, 1, {
      name: 'Mary',
      age: '20',
    }),
    createInstances('user_task_with_form_rerender_2', 1, 1, {
      name: 'Stuart',
      age: '30',
    }),
  ]);
});

test.beforeEach(async ({page}) => {
  await page.goto('/login');
  await page.getByPlaceholder('Username').fill('demo');
  await page.getByPlaceholder('Password').fill('demo');
  await page.getByRole('button', {name: 'Login'}).click();
  await expect(page).toHaveURL('/');
});

test.describe('task details page', () => {
  test('load task details when a task is selected', async ({page}) => {
    await page
      .getByTitle('Available tasks')
      .getByText('usertask_to_be_completed')
      .nth(0)
      .click();

    await test.step('check details header', async () => {
      const taskDetailsHeader = page.getByTitle('Task details header');

      await expect(taskDetailsHeader).toBeVisible();
      await expect(
        taskDetailsHeader.getByText('Some user activity'),
      ).toBeVisible();
      await expect(
        taskDetailsHeader.getByText('usertask_to_be_completed'),
      ).toBeVisible();
      await expect(taskDetailsHeader.getByText('Unassigned')).toBeVisible();
      await expect(
        taskDetailsHeader.getByRole('button', {name: 'Assign to me'}),
      ).toBeVisible();
    });

    await test.step('check details panel', async () => {
      const detailsPanel = page.getByRole('complementary', {
        name: 'Task details right panel',
      });

      await expect(detailsPanel).toBeVisible();
      await expect(detailsPanel.getByText('Creation date')).toBeVisible();
      await expect(
        detailsPanel.getByText(
          /^\d{2}\s\w{3}\s\d{4}\s-\s\d{2}:\d{2}\s(AM|PM)$/,
        ),
      ).toBeVisible();
      await expect(
        detailsPanel.getByText('Candidates', {exact: true}),
      ).toBeVisible();
      await expect(detailsPanel.getByText('No candidates')).toBeVisible();
      await expect(detailsPanel.getByText('Completion date')).toBeVisible();
      await expect(detailsPanel.getByText('Pending task')).toBeVisible();
      await expect(
        detailsPanel.getByText('Due date', {exact: true}),
      ).toBeVisible();
      await expect(detailsPanel.getByText('No due date')).toBeVisible();
      await expect(
        detailsPanel.getByText('Follow up date', {exact: true}),
      ).toBeVisible();
      await expect(detailsPanel.getByText('No follow up date')).toBeVisible();
    });
  });

  test('assign and unassign task', async ({page}) => {
    await page
      .getByTitle('Available tasks')
      .getByText('usertask_to_be_completed')
      .nth(0)
      .click();

    await test.step('assign task', async () => {
      await expect(
        page.getByRole('button', {name: 'Assign to me'}),
      ).toBeVisible();

      await expect(
        page.getByRole('button', {name: 'Assign to me'}),
      ).toBeVisible();

      await expect(page.getByRole('button', {name: 'Complete'})).toBeDisabled();

      await page.getByRole('button', {name: 'Assign to me'}).click();

      await expect(page.getByRole('button', {name: 'Unassign'})).toBeVisible();
      await expect(page.getByRole('button', {name: 'Complete'})).toBeEnabled();
      await expect(page.getByTestId('assignee')).toHaveText('Assigned to me');
    });

    await test.step('unassign task', async () => {
      await page.getByRole('button', {name: 'Unassign'}).click();

      await expect(
        page.getByRole('button', {name: 'Assign to me'}),
      ).toBeVisible();
      await expect(page.getByRole('button', {name: 'Complete'})).toBeDisabled();
      await expect(page.getByTestId('assignee')).toHaveText('Unassigned');
    });

    await page.reload();

    await expect(
      page.getByRole('button', {name: 'Complete Task'}),
    ).toBeDisabled();
  });

  test('complete task', async ({page}) => {
    await page
      .getByTitle('Available tasks')
      .getByText('usertask_to_be_completed')
      .nth(0)
      .click();

    await expect(page.getByText('Pending task')).toBeVisible();

    const taskUrl = page.url();

    await page.getByRole('button', {name: 'Assign to me'}).click();
    await page.getByRole('button', {name: 'Complete Task'}).click();

    await expect(
      page.getByRole('heading', {name: 'Pick a task to work on'}),
    ).toBeVisible();

    await page.goto(taskUrl);

    await expect(
      page.getByRole('button', {name: 'Assign to me'}),
    ).not.toBeVisible();
    await expect(
      page.getByRole('button', {name: 'Unassign'}),
    ).not.toBeVisible();
    await expect(
      page.getByRole('button', {name: 'Complete Task'}),
    ).not.toBeVisible();
    await expect(page.getByText('Pending task')).not.toBeVisible();
  });

  test('task completion with form', async ({page}) => {
    await page
      .getByTitle('Available tasks')
      .getByText('User registration')
      .nth(0)
      .click();

    await test.step('fill form', async () => {
      await expect(page.getByLabel('Name*')).toBeVisible();

      await page.getByRole('button', {name: 'Assign to me'}).click();

      await expect(page.getByRole('button', {name: 'Unassign'})).toBeVisible();

      await page.getByLabel('Name*').fill('Jon');
      await page.getByLabel('Address*').fill('Earth');
      await page.getByLabel('Age').fill('21');
    });

    await page.getByRole('button', {name: 'Complete Task'}).click();

    await expect(page.getByText('Task completed')).toBeVisible();

    await test.step('open completed task', async () => {
      await page.getByRole('combobox', {name: 'Filter options'}).click();
      await page
        .getByRole('option', {name: 'Completed'})
        .getByText('Completed')
        .click();
      await page
        .getByTitle('Available tasks')
        .getByText('User registration')
        .nth(0)
        .click();
    });

    await test.step('check form values', async () => {
      await expect(page.getByLabel('Name*')).toHaveValue('Jon');
      await expect(page.getByLabel('Address*')).toHaveValue('Earth');
      await expect(page.getByLabel('Age')).toHaveValue('21');
    });
  });

  test('task completion with form from assigned to me filter', async ({
    page,
  }) => {
    await test.step('assign task', async () => {
      await page
        .getByTitle('Available tasks')
        .getByText('User registration', {exact: true})
        .nth(0)
        .click();
      await page.getByRole('button', {name: 'Assign to me'}).click();
      await expect(page.getByRole('button', {name: 'Complete'})).toBeEnabled();
    });

    await test.step('open task assigned to me', async () => {
      await page.getByRole('combobox', {name: 'Filter options'}).click();
      await page
        .getByRole('option', {name: 'Assigned to me'})
        .getByText('Assigned to me')
        .click();
      await page
        .getByTitle('Available tasks')
        .getByText('User registration')
        .nth(0)
        .click();
    });

    await test.step('fill form', async () => {
      await expect(page.getByLabel('Name*')).toBeVisible();

      await page.getByLabel('Name*').fill('Gaius Julius Caesar');
      await page.getByLabel('Address*').fill('Rome');
      await page.getByLabel('Age').fill('55');
    });

    await test.step('complete task', async () => {
      await page.getByRole('button', {name: 'Complete Task'}).click();
      await expect(page.getByText('Task completed')).toBeVisible();
    });

    await expect(
      page.getByTitle('Available tasks').getByText('User registration'),
    ).not.toBeVisible();
  });

  test('task completion with prefilled form', async ({page}) => {
    await page
      .getByTitle('Available tasks')
      .getByText('User registration with vars')
      .nth(0)
      .click();

    await page.getByRole('button', {name: 'Assign to me'}).click();

    await test.step('check preffiled form values', async () => {
      await expect(page.getByLabel('Name*')).toHaveValue('Jane');
      await expect(page.getByLabel('Address*')).toHaveValue('');
      await expect(page.getByLabel('Age')).toHaveValue('50');
    });

    await test.step('change form values', async () => {
      await page.getByLabel('Name*').fill('Jon');
      await page.getByLabel('Address*').fill('Earth');
      await page.getByLabel('Age').fill('21');
    });

    await page.getByRole('button', {name: 'Complete Task'}).click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await test.step('open completed task', async () => {
      await page.getByRole('combobox', {name: 'Filter options'}).click();
      await page
        .getByRole('option', {name: 'Completed'})
        .getByText('Completed')
        .click();

      await page
        .getByTitle('Available tasks')
        .getByText('User registration with vars')
        .nth(0)
        .click();
    });

    await test.step('check form values', async () => {
      await expect(page.getByLabel('Name*')).toHaveValue('Jon');
      await expect(page.getByLabel('Address*')).toHaveValue('Earth');
      await expect(page.getByLabel('Age')).toHaveValue('21');
    });
  });

  test('should rerender forms properly', async ({page}) => {
    await page
      .getByTitle('Available tasks')
      .getByText('User Task with form rerender 1')
      .nth(0)
      .click();

    await expect(page.getByLabel('Name*')).toHaveValue('Mary');

    await page
      .getByTitle('Available tasks')
      .getByText('User Task with form rerender 2')
      .nth(0)
      .click();

    await expect(page.getByLabel('Name*')).toHaveValue('Stuart');
  });
});
