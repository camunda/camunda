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
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.openTask('usertask_without_variables');
    await expect(taskDetailsPageV1.emptyTaskMessage).toBeVisible({
      timeout: 30000,
    });
  });

  test('display variables when task has variables', async ({
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('usertask_with_variables');

    await expect(taskDetailsPageV1.emptyTaskMessage).toBeHidden();
    await expect(taskDetailsPageV1.nameColumnHeader).toBeVisible();
    await expect(taskDetailsPageV1.valueColumnHeader).toBeVisible();
    await expect(
      taskDetailsPageV1.variablesTable.getByRole('cell', {name: 'testData'}),
    ).toBeVisible();
    await expect(
      taskDetailsPageV1.variablesTable.getByText('something'),
    ).toBeVisible();
  });

  test('edited variable is saved after refresh if task is completed', async ({
    page,
    taskDetailsPageV1,
    taskPanelPageV1,
  }) => {
    await taskPanelPageV1.openTask('usertask_with_variables');

    await expect(taskDetailsPageV1.assignToMeButton).toBeVisible();
    await expect(taskDetailsPageV1.completeTaskButton).toBeDisabled();
    await taskDetailsPageV1.clickAssignToMeButton();

    await expect(taskDetailsPageV1.unassignButton).toBeVisible();
    await expect(taskDetailsPageV1.completeTaskButton).toBeEnabled();
    await expect(taskDetailsPageV1.assignee).toHaveText('Assigned to me');
    await expect(
      taskDetailsPageV1.variablesTable.getByTitle('testData value'),
    ).toHaveValue('"something"');
    await taskDetailsPageV1.replaceExistingVariableValue({
      name: 'testData value',
      value: '"updatedValue"',
    });
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();

    await expect(taskDetailsPageV1.pickATaskHeader).toBeVisible();
    await page.reload();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.openTask('usertask_with_variables');

    await expect(
      taskDetailsPageV1.variablesTable.getByText('something'),
    ).toBeHidden();
    await expect(
      taskDetailsPageV1.variablesTable.getByText('updatedValue'),
    ).toBeVisible();
  });

  test('edited variable is not saved after refresh', async ({
    page,
    taskDetailsPageV1,
    taskPanelPageV1,
  }) => {
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('usertask_with_variables');

    await expect(taskDetailsPageV1.assignToMeButton).toBeVisible();
    await expect(taskDetailsPageV1.completeTaskButton).toBeDisabled();
    await taskDetailsPageV1.clickAssignToMeButton();

    await expect(taskDetailsPageV1.unassignButton).toBeVisible();
    await expect(taskDetailsPageV1.completeTaskButton).toBeEnabled();
    await expect(taskDetailsPageV1.assignee).toHaveText('Assigned to me');
    await expect(
      taskDetailsPageV1.variablesTable.getByTitle('testData value'),
    ).toBeVisible();
    await expect(
      taskDetailsPageV1.variablesTable.getByTitle('testData value'),
    ).toHaveValue('"something"');

    await taskDetailsPageV1.replaceExistingVariableValue({
      name: 'testData value',
      value: '"updatedValue"',
    });
    await page.reload();
    await expect(
      taskDetailsPageV1.variablesTable.getByTitle('testData value'),
    ).toHaveValue('"something"');
    await expect(
      taskDetailsPageV1.variablesTable.getByTitle('testData value'),
    ).not.toHaveValue('"updatedValue"');
  });

  test('new variable disappears after refresh', async ({
    page,
    taskDetailsPageV1,
    taskPanelPageV1,
  }) => {
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('usertask_with_variables');

    await expect(taskDetailsPageV1.addVariableButton).toBeHidden();
    await expect(taskDetailsPageV1.assignToMeButton).toBeVisible();
    await taskDetailsPageV1.clickAssignToMeButton();

    await taskDetailsPageV1.addVariable({
      name: 'newVariableName',
      value: '"newVariableValue"',
    });

    await page.reload();

    await expect(
      taskDetailsPageV1.variablesTable.getByText('newVariableName'),
    ).toBeHidden();
    await expect(
      taskDetailsPageV1.variablesTable.getByText('newVariableValue'),
    ).toBeHidden();
  });

  test('new variable still exists after refresh if task is completed', async ({
    page,
    taskDetailsPageV1,
    taskPanelPageV1,
  }) => {
    test.slow();
    await taskPanelPageV1.filterBy('Unassigned');
    await taskPanelPageV1.openTask('usertask_with_variables');

    await expect(taskDetailsPageV1.addVariableButton).toBeHidden();
    await expect(taskDetailsPageV1.assignToMeButton).toBeVisible();
    await expect(taskDetailsPageV1.completeTaskButton).toBeDisabled();
    await taskDetailsPageV1.clickAssignToMeButton();

    await expect(taskDetailsPageV1.unassignButton).toBeVisible();
    await expect(taskDetailsPageV1.completeTaskButton).toBeEnabled();
    await expect(taskDetailsPageV1.assignee).toHaveText('Assigned to me');

    await taskDetailsPageV1.addVariable({
      name: 'newVariableName',
      value: '"newVariableValue"',
    });

    await taskDetailsPageV1.completeTaskButton.click();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible();
    await expect(taskDetailsPageV1.pickATaskHeader).toBeVisible();

    await page.reload();

    await taskPanelPageV1.filterBy('Completed');
    await taskPanelPageV1.openTask('usertask_with_variables');

    await expect(page.getByText('newVariableName')).toBeVisible({
      timeout: 30000,
    });
    await expect(page.getByText('newVariableValue')).toBeVisible();
  });
});
