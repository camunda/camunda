/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
    await expect(
      page.getByText('We will redirect you to the task once it is created'),
    ).toBeVisible();
    await expect(page.getByText('Task has no variables')).toBeVisible({
      timeout: 10000,
    });
  });

  test('complete task started by process instance', async ({
    page,
    mainPage,
    taskDetailsPage,
    processesPage,
  }) => {
    await mainPage.clickProcessesTab();
    await expect(page).toHaveURL('/processes');
    await processesPage.clickContinueButton();

    await processesPage.searchForProcess('User_Process');
    await expect(processesPage.processTile).toHaveCount(1, {timeout: 10000});

    await processesPage.clickStartProcessButton();
    await expect(page.getByText('Task has no variables')).toBeVisible({
      timeout: 10000,
    });

    await taskDetailsPage.assignToMeButton.click();
    await taskDetailsPage.completeButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();
  });
});
