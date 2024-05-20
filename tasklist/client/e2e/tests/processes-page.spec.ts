/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {deploy} from '../zeebeClient';

test.afterAll(async ({resetData}) => {
  await resetData();
});

test.beforeAll(async () => {
  await deploy('./e2e/resources/user_process.bpmn');
  await deploy('./e2e/resources/processWithStartNodeFormDeployed.bpmn');
  await deploy('./e2e/resources/create-invoice_8-5.form');
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

  test('start process instance', async ({
    page,
    mainPage,
    processesPage,
    taskDetailsPage,
  }) => {
    await mainPage.clickProcessesTab();
    await expect(page).toHaveURL('/processes');
    await processesPage.clickContinueButton();

    await processesPage.searchForProcess('User_Process');
    await expect(processesPage.processTile).toHaveCount(1, {timeout: 10000});

    await processesPage.clickStartProcessButton();
    await expect(page.getByText('Process has started')).toBeVisible();
    await expect(processesPage.startProcessButton).not.toBeVisible();
    await expect(page.getByText('Waiting for tasks...')).toBeVisible();
    await expect(taskDetailsPage.assignToMeButton).toBeVisible();
  });

  test('complete task started by process instance', async ({
    page,
    mainPage,
    taskDetailsPage,
    formJSDetailsPage,
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
    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();
  });

  test('complete process with start node having deployed form', async ({
    page,
    mainPage,
    taskDetailsPage,
    formJSDetailsPage,
    processesPage,
    taskPanelPage,
  }) => {
    await mainPage.clickProcessesTab();
    await expect(page).toHaveURL('/processes');
    await processesPage.clickContinueButton();
    await processesPage.searchForProcess('processWithStartNodeFormDeployed');
    await expect(processesPage.processTile).toHaveCount(1, {timeout: 30000});

    await processesPage.clickStartProcessButton();
    await page.getByLabel('Client Name*').fill('Jon');
    await page.getByLabel('Client Address*').fill('Earth');
    await formJSDetailsPage.fillDateField('Invoice Date*', '1/1/3000');
    await formJSDetailsPage.fillDateField('Due Date*', '1/2/3000');
    await page.getByLabel('Invoice Number*').fill('123');
    await formJSDetailsPage.selectDropdownOption(
      'USD - United States Dollar',
      'EUR - Euro',
    );
    await page.getByRole('button', {name: /add new/i}).click();
    await formJSDetailsPage.forEachDynamicListItem(
      page.getByLabel('Item Name*'),
      async (element, index) => {
        await element.fill(`${'Laptop'}${index + 1}`);
      },
    );
    await formJSDetailsPage.forEachDynamicListItem(
      page.getByLabel('Unit Price*'),
      async (element, index) => {
        await element.fill(`${'1'}${index + 1}`);
      },
    );
    await formJSDetailsPage.forEachDynamicListItem(
      page.getByLabel('Quantity*'),
      async (element, index) => {
        await element.clear();
        await element.fill(`${'2'}${index + 1}`);
      },
    );

    await expect(page.getByText('EUR 231')).toBeVisible();
    await expect(page.getByText('EUR 264')).toBeVisible();
    await expect(page.getByText('Total: EUR 544.5')).toBeVisible();
    await processesPage.startProcessSubButton.click();

    await processesPage.tasksTab.click();
    await taskPanelPage.openTask('processStartedByForm_user_task');
    await taskDetailsPage.assignToMeButton.click();
    await expect(
      page.getByText('{"name":"jon","address":"earth"}'),
    ).toBeVisible();
    await expect(page.getByText('EUR')).toBeVisible();
    await expect(page.getByText('3000-01-01')).toBeVisible();
    await expect(page.getByText('3000-01-02')).toBeVisible();
    await expect(page.getByText('123')).toBeVisible();
    await expect(
      page.getByText(
        '[{"itemName":"laptop1","unitPrice":11,"quantity":21},{"itemName":"laptop2","unitPrice":12,"quantity":22}]',
      ),
    ).toBeVisible();

    await formJSDetailsPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();
  });
});
