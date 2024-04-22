/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {expect} from '@playwright/test';
import {deploy, createInstances} from '../zeebeClient';
import {test} from '../test-fixtures';

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
  await expect(page).toHaveURL('/');
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
