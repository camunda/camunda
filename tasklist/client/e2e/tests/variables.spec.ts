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

test.beforeEach(async ({page, testSetupPage, loginPage}) => {
  await testSetupPage.goToLoginPage();
  await loginPage.login({
    username: 'demo',
    password: 'demo',
  });
  await expect(page).toHaveURL('../tasklist');
});

test.describe('variables page', () => {
  test('display info message when task has no variables', async ({
    page,
    taskPanelPage,
  }) => {
    await taskPanelPage.openTask('usertask_without_variables');
    await expect(page.getByText('Task has no variables')).toBeVisible();
  });

  test('display variables when task has variables', async ({
    page,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('usertask_with_variables');

    await expect(page.getByText('Task has no variables')).not.toBeVisible();
    await expect(taskDetailsPage.nameColumnHeader).toBeVisible();
    await expect(taskDetailsPage.valueColumnHeader).toBeVisible();
    await expect(
      taskDetailsPage.variablesTable.getByRole('cell', {name: 'testData'}),
    ).toBeVisible();
    await expect(
      taskDetailsPage.variablesTable.getByText('something'),
    ).toBeVisible();
  });

  test('edited variable is saved after refresh if task is completed', async ({
    page,
    taskDetailsPage,
    formJSDetailsPage,
    taskPanelPage,
  }) => {
    await taskPanelPage.openTask('usertask_with_variables');

    await expect(taskDetailsPage.assignToMeButton).toBeVisible();
    await expect(formJSDetailsPage.completeTaskButton).toBeDisabled();
    await taskDetailsPage.assignToMeButton.click();

    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await expect(formJSDetailsPage.completeTaskButton).toBeEnabled();
    await expect(taskDetailsPage.assignee).toHaveText('Assigned to me');
    await expect(
      taskDetailsPage.variablesTable.getByTitle('testData value'),
    ).toHaveValue('"something"');
    await taskDetailsPage.replaceExistingVariableValue({
      name: 'testData value',
      value: '"updatedValue"',
    });
    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();

    await expect(taskDetailsPage.pickATaskHeader).toBeVisible();
    await page.reload();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('usertask_with_variables');

    await expect(
      taskDetailsPage.variablesTable.getByText('something'),
    ).not.toBeVisible();
    await expect(
      taskDetailsPage.variablesTable.getByText('updatedValue'),
    ).toBeVisible();
  });

  test('edited variable is not saved after refresh', async ({
    page,
    taskDetailsPage,
    formJSDetailsPage,
    taskPanelPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('usertask_with_variables');

    await expect(taskDetailsPage.assignToMeButton).toBeVisible();
    await expect(formJSDetailsPage.completeTaskButton).toBeDisabled();
    await taskDetailsPage.assignToMeButton.click();

    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await expect(formJSDetailsPage.completeTaskButton).toBeEnabled();
    await expect(taskDetailsPage.assignee).toHaveText('Assigned to me');
    await expect(
      taskDetailsPage.variablesTable.getByTitle('testData value'),
    ).toBeVisible();
    await expect(
      taskDetailsPage.variablesTable.getByTitle('testData value'),
    ).toHaveValue('"something"');

    await taskDetailsPage.replaceExistingVariableValue({
      name: 'testData value',
      value: '"updatedValue"',
    });
    await page.reload();
    await expect(
      taskDetailsPage.variablesTable.getByTitle('testData value'),
    ).toHaveValue('"something"');
    await expect(
      taskDetailsPage.variablesTable.getByTitle('testData value'),
    ).not.toHaveValue('"updatedValue"');
  });

  test('new variable disappears after refresh', async ({
    page,
    taskDetailsPage,
    taskPanelPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('usertask_with_variables');

    await expect(taskDetailsPage.addVariableButton).not.toBeVisible();
    await expect(taskDetailsPage.assignToMeButton).toBeVisible();
    await taskDetailsPage.assignToMeButton.click();

    await taskDetailsPage.addVariable({
      name: 'newVariableName',
      value: '"newVariableValue"',
    });

    await page.reload();

    await expect(
      taskDetailsPage.variablesTable.getByText('newVariableName'),
    ).not.toBeVisible();
    await expect(
      taskDetailsPage.variablesTable.getByText('newVariableValue'),
    ).not.toBeVisible();
  });

  test('new variable still exists after refresh if task is completed', async ({
    page,
    taskDetailsPage,
    formJSDetailsPage,
    taskPanelPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('usertask_with_variables');

    await expect(taskDetailsPage.addVariableButton).not.toBeVisible();
    await expect(taskDetailsPage.assignToMeButton).toBeVisible();
    await expect(formJSDetailsPage.completeTaskButton).toBeDisabled();
    await taskDetailsPage.assignToMeButton.click();

    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await expect(formJSDetailsPage.completeTaskButton).toBeEnabled();
    await expect(taskDetailsPage.assignee).toHaveText('Assigned to me');

    await taskDetailsPage.addVariable({
      name: 'newVariableName',
      value: '"newVariableValue"',
    });

    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();
    await expect(taskDetailsPage.pickATaskHeader).toBeVisible();

    await page.reload();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('usertask_with_variables');

    await expect(page.getByText('newVariableName')).toBeVisible();
    await expect(page.getByText('newVariableValue')).toBeVisible();
  });
});
