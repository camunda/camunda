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
  await deploy('./e2e/resources/usertask_with_variables.bpmn');
  await deploy('./e2e/resources/usertask_without_variables.bpmn');
  await createInstances('usertask_without_variables', 1, 1);
  await createInstances('usertask_with_variables', 1, 4, {
    testData: 'something',
  });
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

test.describe('variables page', () => {
  test('display info message when task has no variables', async ({
    page,
    tasksPage,
  }) => {
    await tasksPage.openTask('usertask_without_variables');
    await expect(page.getByText('Task has no variables')).toBeVisible();
  });

  test('display variables when task has variables', async ({
    page,
    tasksPage,
    taskVariableView,
  }) => {
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('usertask_with_variables');

    await expect(page.getByText('Task has no variables')).not.toBeVisible();
    await expect(taskVariableView.nameColumnHeader).toBeVisible();
    await expect(taskVariableView.valueColumnHeader).toBeVisible();
    await expect(
      taskVariableView.variablesTable.getByRole('cell', {name: 'testData'}),
    ).toBeVisible();
    await expect(
      taskVariableView.variablesTable.getByText('something'),
    ).toBeVisible();
  });

  test('edited variable is saved after refresh if task is completed', async ({
    page,
    taskVariableView,
    tasksPage,
  }) => {
    await tasksPage.openTask('usertask_with_variables');

    await expect(tasksPage.assignToMeButton).toBeVisible();
    await expect(tasksPage.completeTaskButton).toBeDisabled();
    await tasksPage.assignToMeButton.click();

    await expect(tasksPage.unassignButton).toBeVisible();
    await expect(tasksPage.completeTaskButton).toBeEnabled();
    await expect(tasksPage.assignee).toHaveText('Assigned to me');
    await expect(
      taskVariableView.variablesTable.getByTitle('testData value'),
    ).toHaveValue('"something"');
    await taskVariableView.replaceExistingVariableValue({
      name: 'testData value',
      value: '"updatedValue"',
    });
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await expect(tasksPage.pickATaskHeader).toBeVisible();
    await page.reload();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('usertask_with_variables');

    await expect(
      taskVariableView.variablesTable.getByText('something'),
    ).not.toBeVisible();
    await expect(
      taskVariableView.variablesTable.getByText('updatedValue'),
    ).toBeVisible();
  });

  test('edited variable is not saved after refresh', async ({
    page,
    taskVariableView,
    tasksPage,
  }) => {
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('usertask_with_variables');

    await expect(tasksPage.assignToMeButton).toBeVisible();
    await expect(tasksPage.completeTaskButton).toBeDisabled();
    await tasksPage.assignToMeButton.click();

    await expect(tasksPage.unassignButton).toBeVisible();
    await expect(tasksPage.completeTaskButton).toBeEnabled();
    await expect(tasksPage.assignee).toHaveText('Assigned to me');
    await expect(
      taskVariableView.variablesTable.getByTitle('testData value'),
    ).toBeVisible();
    await expect(
      taskVariableView.variablesTable.getByTitle('testData value'),
    ).toHaveValue('"something"');

    await taskVariableView.replaceExistingVariableValue({
      name: 'testData value',
      value: '"updatedValue"',
    });
    await page.reload();
    await expect(
      taskVariableView.variablesTable.getByTitle('testData value'),
    ).toHaveValue('"something"');
    await expect(
      taskVariableView.variablesTable.getByTitle('testData value'),
    ).not.toHaveValue('"updatedValue"');
  });

  test('new variable disappears after refresh', async ({
    page,
    taskVariableView,
    tasksPage,
  }) => {
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('usertask_with_variables');

    await expect(taskVariableView.addVariableButton).not.toBeVisible();
    await expect(tasksPage.assignToMeButton).toBeVisible();
    await tasksPage.assignToMeButton.click();

    await taskVariableView.addVariable({
      name: 'newVariableName',
      value: '"newVariableValue"',
    });

    await page.reload();

    await expect(
      taskVariableView.variablesTable.getByText('newVariableName'),
    ).not.toBeVisible();
    await expect(
      taskVariableView.variablesTable.getByText('newVariableValue'),
    ).not.toBeVisible();
  });

  test('new variable still exists after refresh if task is completed', async ({
    page,
    taskVariableView,
    tasksPage,
  }) => {
    await tasksPage.filterBy('Unassigned');
    await tasksPage.openTask('usertask_with_variables');

    await expect(taskVariableView.addVariableButton).not.toBeVisible();
    await expect(tasksPage.assignToMeButton).toBeVisible();
    await expect(tasksPage.completeTaskButton).toBeDisabled();
    await tasksPage.assignToMeButton.click();

    await expect(tasksPage.unassignButton).toBeVisible();
    await expect(tasksPage.completeTaskButton).toBeEnabled();
    await expect(tasksPage.assignee).toHaveText('Assigned to me');

    await taskVariableView.addVariable({
      name: 'newVariableName',
      value: '"newVariableValue"',
    });

    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();
    await expect(tasksPage.pickATaskHeader).toBeVisible();

    await page.reload();

    await tasksPage.filterBy('Completed');
    await tasksPage.openTask('usertask_with_variables');

    await expect(page.getByText('newVariableName')).toBeVisible();
    await expect(page.getByText('newVariableValue')).toBeVisible();
  });
});
