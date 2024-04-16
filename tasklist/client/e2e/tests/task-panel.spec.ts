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
    test.slow();
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
