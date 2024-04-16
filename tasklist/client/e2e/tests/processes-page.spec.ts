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

import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {deploy} from '../zeebeClient';

test.afterAll(async ({resetData}) => {
  await resetData();
});

test.beforeAll(async () => {
  await deploy('./e2e/resources/user_process.bpmn');
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  await sleep(27000);
});

test.beforeEach(async ({testSetupPage, loginPage, page}) => {
  await testSetupPage.goToLoginPage();
  await loginPage.login({
    username: 'demo',
    password: 'demo',
  });
  await expect(page).toHaveURL('/');
});

test.describe('process page', () => {
  test('process page navigation', async ({mainPage, page, processesPage}) => {
    await mainPage.clickProcessesTab();
    await expect(page).toHaveURL('/processes');
    await expect(page.getByText('Start your process on demand')).toBeVisible();
    await processesPage.clickCancelButton();
    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await mainPage.clickProcessesTab();
    await processesPage.clickContinueButton();
    await expect(page.getByText('Search processes')).toBeVisible();
  });

  test('process searching', async ({page, mainPage, processesPage}) => {
    await mainPage.clickProcessesTab();
    await expect(page).toHaveURL('/processes');
    await processesPage.clickContinueButton();

    await processesPage.searchForProcess('fake_process');
    await expect(
      page.getByText('We could not find any process with that name'),
    ).toBeVisible();

    await processesPage.searchForProcess('User_Process');
    await expect(
      page.getByText('We could not find any process with that name'),
    ).not.toBeVisible();

    await expect(processesPage.processTile).toHaveCount(1);
    await expect(processesPage.processTile).toContainText('User_Process');
  });

  test('start process instance', async ({page, mainPage, processesPage}) => {
    await mainPage.clickProcessesTab();
    await expect(page).toHaveURL('/processes');
    await processesPage.clickContinueButton();

    await processesPage.searchForProcess('User_Process');
    await expect(processesPage.processTile).toHaveCount(1, {timeout: 10000});

    await processesPage.clickStartProcessButton();
    await expect(page.getByText('Process has started')).toBeVisible();
    await expect(processesPage.startProcessButton).not.toBeVisible();
    await expect(page.getByText('Waiting for tasks...')).toBeVisible();
    await expect(processesPage.startProcessButton).toBeVisible();
  });

  test('complete task started by process instance', async ({
    page,
    mainPage,
    taskDetailsPage,
    processesPage,
    taskPanelPage,
  }) => {
    await mainPage.clickProcessesTab();
    await expect(page).toHaveURL('/processes');
    await processesPage.clickContinueButton();

    await processesPage.searchForProcess('User_Process');
    await expect(processesPage.processTile).toHaveCount(1, {timeout: 10000});

    await processesPage.clickStartProcessButton();
    await processesPage.tasksTab.click();

    await taskPanelPage.openTask('User_Task');

    await taskDetailsPage.assignToMeButton.click();
    await taskDetailsPage.completeButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();
  });
});
