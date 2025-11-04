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
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';

test.beforeAll(async () => {
  await deploy([
    './resources/usertask_with_variables.bpmn',
    './resources/usertask_without_variables.bpmn',
  ]);
  await createInstances('usertask_without_variables', 1, 1);
  await createInstances('usertask_with_variables', 1, 4, {
    testData: 'something',
  });
});

test.describe('variables page', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('display info message when task has no variables', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.openTask('usertask_without_variables');
    await expect(taskDetailsPage.emptyTaskMessage).toBeVisible();
  });

  test('display variables when task has variables', async ({
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('usertask_with_variables');

    await expect(taskDetailsPage.emptyTaskMessage).toBeHidden();
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
    taskPanelPage,
  }) => {
    await taskPanelPage.openTask('usertask_with_variables');

    await expect(taskDetailsPage.assignToMeButton).toBeVisible();
    await expect(taskDetailsPage.completeTaskButton).toBeDisabled();
    await taskDetailsPage.clickAssignToMeButton();

    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await expect(taskDetailsPage.completeTaskButton).toBeEnabled();
    await expect(taskDetailsPage.assignee).toHaveText('Assigned to me');
    await expect(
      taskDetailsPage.variablesTable.getByTitle('testData value'),
    ).toHaveValue('"something"');
    await taskDetailsPage.replaceExistingVariableValue({
      name: 'testData value',
      value: '"updatedValue"',
    });
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();

    await expect(taskDetailsPage.pickATaskHeader).toBeVisible();
    await page.reload();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('usertask_with_variables');

    await expect(
      taskDetailsPage.variablesTable.getByText('something'),
    ).toBeHidden();
    await expect(
      taskDetailsPage.variablesTable.getByText('updatedValue'),
    ).toBeVisible();
  });

  test('edited variable is not saved after refresh', async ({
    page,
    taskDetailsPage,
    taskPanelPage,
  }) => {
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('usertask_with_variables');

    await expect(taskDetailsPage.assignToMeButton).toBeVisible();
    await expect(taskDetailsPage.completeTaskButton).toBeDisabled();
    await taskDetailsPage.clickAssignToMeButton();

    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await expect(taskDetailsPage.completeTaskButton).toBeEnabled();
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

    await expect(taskDetailsPage.addVariableButton).toBeHidden();
    await expect(taskDetailsPage.assignToMeButton).toBeVisible();
    await taskDetailsPage.clickAssignToMeButton();

    await taskDetailsPage.addVariable({
      name: 'newVariableName',
      value: '"newVariableValue"',
    });

    await page.reload();

    await expect(
      taskDetailsPage.variablesTable.getByText('newVariableName'),
    ).toBeHidden();
    await expect(
      taskDetailsPage.variablesTable.getByText('newVariableValue'),
    ).toBeHidden();
  });

  test('new variable still exists after refresh if task is completed', async ({
    page,
    taskDetailsPage,
    taskPanelPage,
  }) => {
    test.slow();
    await taskPanelPage.filterBy('Unassigned');
    await taskPanelPage.openTask('usertask_with_variables');

    await expect(taskDetailsPage.addVariableButton).toBeHidden();
<<<<<<< HEAD
    await expect(taskDetailsPage.assignToMeButton).toBeVisible({timeout: 60000});
=======
    await expect(taskDetailsPage.assignToMeButton).toBeVisible({timeout: 30000});
>>>>>>> 81660d53 (test: increase timeouts for slow test behaviour)
    await expect(taskDetailsPage.completeTaskButton).toBeDisabled();
    await taskDetailsPage.clickAssignToMeButton();

    await expect(taskDetailsPage.unassignButton).toBeVisible();
    await expect(taskDetailsPage.completeTaskButton).toBeEnabled();
    await expect(taskDetailsPage.assignee).toHaveText('Assigned to me');

    await taskDetailsPage.addVariable({
      name: 'newVariableName',
      value: '"newVariableValue"',
    });

    await taskDetailsPage.completeTaskButton.click();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible();
    await expect(taskDetailsPage.pickATaskHeader).toBeVisible();

    await page.reload();

    await taskPanelPage.filterBy('Completed');
    await taskPanelPage.openTask('usertask_with_variables');

    await expect(page.getByText('newVariableName')).toBeVisible({timeout: 60000});
    await expect(page.getByText('newVariableValue')).toBeVisible();
  });
});
