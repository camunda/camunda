/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@/test-fixtures';
import {expect} from '@playwright/test';
import {deploy} from '@/utils/zeebeClient';
import {sleep} from '@/utils/sleep';

test.afterAll(async ({resetData}) => {
  await resetData();
});

test.beforeAll(async () => {
  await deploy([
    './e2e/resources/user_process.bpmn',
    './e2e/resources/processWithStartNodeFormDeployed.bpmn',
    './e2e/resources/create_invoice.form',
  ]);

  await sleep(2000);
});

test.beforeEach(async ({loginPage, page}) => {
  await loginPage.goto();
  await loginPage.login({
    username: 'demo',
    password: 'demo',
  });
  await expect(page).toHaveURL('/tasklist');
});

test.describe('process page', () => {
  test('process page navigation', async ({header, page, processesPage}) => {
    await header.processesTab.click();
    await expect(page).toHaveURL('/tasklist/processes');
    await expect(page.getByText('Start your process on demand')).toBeVisible();
    await processesPage.cancelButton.click();
    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await header.processesTab.click();
    await processesPage.continueButton.click();
    await expect(page.getByText('Search processes')).toBeVisible();
  });

  test('process searching', async ({page, header, processesPage}) => {
    await header.processesTab.click();
    await expect(page).toHaveURL('/tasklist/processes');
    await processesPage.continueButton.click();

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
    header,
    processesPage,
    tasksPage,
  }) => {
    await header.processesTab.click();
    await expect(page).toHaveURL('/tasklist/processes');
    await processesPage.continueButton.click();

    await processesPage.searchForProcess('User_Process');
    await expect(processesPage.processTile).toHaveCount(1, {timeout: 10000});

    await processesPage.startProcessButton.click();
    await expect(page.getByText('Process has started')).toBeVisible();
    await expect(processesPage.startProcessButton).not.toBeVisible();
    await expect(page.getByText('Waiting for tasks...')).toBeVisible();
    await expect(tasksPage.assignToMeButton).toBeVisible();
  });

  test('complete task started by process instance', async ({
    page,
    header,
    processesPage,
    tasksPage,
  }) => {
    await header.processesTab.click();
    await expect(page).toHaveURL('/tasklist/processes');
    await processesPage.continueButton.click();

    await processesPage.searchForProcess('User_Process');
    await expect(processesPage.processTile).toHaveCount(1, {timeout: 10000});

    await processesPage.startProcessButton.click();
    await processesPage.tasksTab.click();

    await tasksPage.openTask('User_Task');

    await tasksPage.assignToMeButton.click();
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();
  });

  test('complete process with start node having deployed form', async ({
    page,
    header,
    taskFormView,
    processesPage,
    tasksPage,
  }) => {
    await header.processesTab.click();
    await expect(page).toHaveURL('/tasklist/processes');
    await processesPage.continueButton.click();
    await processesPage.searchForProcess('processWithStartNodeFormDeployed');
    await expect(processesPage.processTile).toHaveCount(1, {
      timeout: 30000,
    });

    await processesPage.startProcessButton.click();
    await page.getByRole('textbox', {name: 'Client Name'}).fill('Jon');
    await page.getByRole('textbox', {name: 'Client Address'}).fill('Earth');
    await taskFormView.fillDatetimeField('Invoice Date', '1/1/3000');
    await taskFormView.fillDatetimeField('Due Date', '1/2/3000');
    await taskFormView.selectDropdownOption(
      'USD - United States Dollar',
      'EUR - Euro',
    );
    await page.getByRole('textbox', {name: 'Invoice Number'}).fill('123');
    await page.getByRole('button', {name: /add new/i}).click();
    await taskFormView.forEachDynamicListItem(
      page.getByLabel('Item Name*'),
      async (element, index) => {
        await element.fill(`${'Laptop'}${index + 1}`);
      },
    );
    await taskFormView.forEachDynamicListItem(
      page.getByLabel('Unit Price*'),
      async (element, index) => {
        await element.fill(`${'1'}${index + 1}`);
      },
    );
    await taskFormView.forEachDynamicListItem(
      page.getByLabel('Quantity*'),
      async (element, index) => {
        await element.clear();
        await element.fill(`${'2'}${index + 1}`);
      },
    );

    await expect(page.getByText('EUR 231')).toBeVisible();
    await expect(page.getByText('EUR 264')).toBeVisible();
    await expect(page.getByText('Total: EUR 544.5')).toBeVisible();
    await processesPage.modaLStartProcessButton.click();

    await processesPage.tasksTab.click();
    await tasksPage.openTask('processStartedByForm_user_task');
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
    await tasksPage.assignToMeButton.click();
    await tasksPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();
  });
});
