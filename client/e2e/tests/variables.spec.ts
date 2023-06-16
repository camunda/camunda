/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test, expect} from '@playwright/test';
import {deploy, createInstances} from '../zeebeClient';

test.beforeAll(async () => {
  await deploy('./e2e/resources/usertask_with_variables.bpmn');
  await deploy('./e2e/resources/usertask_without_variables.bpmn');
  await createInstances('usertask_without_variables', 1, 1);
  await createInstances('usertask_with_variables', 1, 4, {
    testData: 'something',
  });
});

test.beforeEach(async ({page}) => {
  await page.goto('/login');
  await page.getByPlaceholder('Username').fill('demo');
  await page.getByPlaceholder('Password').fill('demo');
  await page.getByRole('button', {name: 'Login'}).click();
  await expect(page).toHaveURL('/');
});

test.describe('variables page', () => {
  test('display info message when task has no variables', async ({page}) => {
    await page
      .getByTitle('Available tasks')
      .getByText('usertask_without_variables')
      .nth(0)
      .click();
    await expect(page.getByText('Task has no variables')).toBeVisible();
  });

  test('display variables when task has variables', async ({page}) => {
    await page.getByRole('combobox', {name: 'Filter options'}).click();
    await page
      .getByRole('option', {name: 'Unassigned'})
      .getByText('Unassigned')
      .click();

    await page
      .getByTitle('Available tasks')
      .getByText('usertask_with_variables')
      .nth(0)
      .click();

    await expect(page.getByText('Task has no variables')).not.toBeVisible();

    const variablesTable = page.getByTestId('variables-table');
    await expect(
      variablesTable.getByRole('columnheader', {name: 'Name'}),
    ).toBeVisible({
      timeout: 10000,
    });
    await expect(
      variablesTable.getByRole('columnheader', {name: 'Value'}),
    ).toBeVisible();
    await expect(
      variablesTable.getByText('testData', {exact: true}),
    ).toBeVisible();
    await expect(variablesTable.getByText('something')).toBeVisible();
  });

  test('edited variable is saved after refresh if task is completed', async ({
    page,
  }) => {
    await page
      .getByTitle('Available tasks')
      .getByText('usertask_with_variables')
      .nth(0)
      .click();

    await test.step('assign task', async () => {
      await expect(
        page.getByRole('button', {name: 'Assign to me'}),
      ).toBeVisible();

      await expect(page.getByRole('button', {name: 'Complete'})).toBeDisabled();

      await page.getByRole('button', {name: 'Assign to me'}).click();

      await expect(page.getByRole('button', {name: 'Unassign'})).toBeVisible();
      await expect(page.getByRole('button', {name: 'Complete'})).toBeEnabled();
      await expect(page.getByTestId('assignee')).toHaveText('Assigned to me');
    });

    await expect(page.getByTitle('testData value')).toHaveValue('"something"');

    await page.getByTitle('testData value').clear();

    await page.getByTitle('testData value').fill('"updatedValue"');

    const variablesTable = page.getByTestId('variables-table');
    await test.step('complete task', async () => {
      await page.getByRole('button', {name: 'Complete Task'}).click();
      await expect(page.getByText('Task completed')).toBeVisible();
    });

    await expect(
      page.getByRole('heading', {name: 'Pick a task to work on'}),
    ).toBeTruthy();

    await page.reload();
    await page.getByRole('combobox', {name: 'Filter options'}).click();
    await page
      .getByRole('option', {name: 'Completed'})
      .getByText('Completed')
      .click();

    await page
      .getByTitle('Available tasks')
      .getByText('usertask_with_variables')
      .first()
      .click();

    await expect(variablesTable.getByText('something')).not.toBeVisible();
    await expect(variablesTable.getByText('updatedValue')).toBeVisible();
  });

  test('edited variable is not saved after refresh', async ({page}) => {
    await page.getByRole('combobox', {name: 'Filter options'}).click();
    await page
      .getByRole('option', {name: 'Unassigned'})
      .getByText('Unassigned')
      .click();

    await page
      .getByTitle('Available tasks')
      .getByText('usertask_with_variables')
      .nth(0)
      .click();

    await test.step('assign task', async () => {
      await expect(
        page.getByRole('button', {name: 'Assign to me'}),
      ).toBeVisible();

      await expect(page.getByRole('button', {name: 'Complete'})).toBeDisabled();

      await page.getByRole('button', {name: 'Assign to me'}).click();

      await expect(page.getByRole('button', {name: 'Unassign'})).toBeVisible();
      await expect(page.getByRole('button', {name: 'Complete'})).toBeEnabled();
      await expect(page.getByTestId('assignee')).toHaveText('Assigned to me');
    });

    await expect(page.getByTitle('testData value')).toBeVisible();
    await expect(page.getByTitle('testData value')).toHaveValue('"something"');

    await page.getByTitle('testData value').fill('"updatedValue"');
    await page.reload();
    await expect(page.getByTitle('testData value')).toHaveValue('"something"');
    await expect(page.getByTitle('testData value')).not.toHaveValue(
      '"updatedValue"',
    );
  });

  test('new variable disappears after refresh', async ({page}) => {
    await page.getByRole('combobox', {name: 'Filter options'}).click();
    await page
      .getByRole('option', {name: 'Unassigned'})
      .getByText('Unassigned')
      .click();

    await page
      .getByTitle('Available tasks')
      .getByText('usertask_with_variables')
      .nth(0)
      .click();

    await expect(
      page.getByRole('button', {name: 'Add Variable'}),
    ).not.toBeVisible();

    await expect(
      page.getByRole('button', {name: 'Assign to me'}),
    ).toBeVisible();

    await page.getByRole('button', {name: 'Assign to me'}).click();
    await page.getByRole('button', {name: 'Add Variable'}).click();

    await page.getByPlaceholder('Name', {exact: true}).fill('newVariableName');
    await page
      .getByPlaceholder('Value', {exact: true})
      .fill('"newVariableValue"');

    await page.reload();

    await expect(page.getByText('newVariableName')).not.toBeVisible();
    await expect(page.getByText('newVariableValue')).not.toBeVisible();
  });

  test('new variable still exists after refresh if task is completed', async ({
    page,
  }) => {
    await page.getByRole('combobox', {name: 'Filter options'}).click();
    await page
      .getByRole('option', {name: 'Unassigned'})
      .getByText('Unassigned')
      .click();

    await page
      .getByTitle('Available tasks')
      .getByText('usertask_with_variables')
      .nth(0)
      .click();

    await expect(
      page.getByRole('button', {name: 'Add Variable'}),
    ).not.toBeVisible();

    await test.step('assign task', async () => {
      await expect(
        page.getByRole('button', {name: 'Assign to me'}),
      ).toBeVisible({
        timeout: 10000,
      });

      await expect(page.getByRole('button', {name: 'Complete'})).toBeDisabled();

      await page.getByRole('button', {name: 'Assign to me'}).click();

      await expect(page.getByRole('button', {name: 'Unassign'})).toBeVisible();
      await expect(page.getByRole('button', {name: 'Complete'})).toBeEnabled();
      await expect(page.getByTestId('assignee')).toHaveText('Assigned to me');
    });

    await page.getByRole('button', {name: 'Add Variable'}).click();

    await page.getByPlaceholder('Name', {exact: true}).fill('newVariableName');
    await page
      .getByPlaceholder('Value', {exact: true})
      .fill('"newVariableValue"');

    await test.step('complete task', async () => {
      await expect(
        page.getByRole('button', {name: 'Complete Task'}),
      ).toBeEnabled();
      await page.getByRole('button', {name: 'Complete Task'}).click();
      await expect(page.getByText('Task completed')).toBeVisible();
    });

    await expect(
      page.getByRole('heading', {name: 'Pick a task to work on'}),
    ).toBeTruthy();

    await page.reload();

    await page.getByRole('combobox', {name: 'Filter options'}).click();
    await page
      .getByRole('option', {name: 'Completed'})
      .getByText('Completed')
      .click();

    await page
      .getByTitle('Available tasks')
      .getByText('usertask_with_variables')
      .nth(0)
      .click();

    await expect(page.getByText('newVariableName')).toBeVisible();
    await expect(page.getByText('newVariableValue')).toBeVisible();
  });
});
