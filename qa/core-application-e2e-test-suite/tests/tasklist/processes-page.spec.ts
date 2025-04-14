/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {deploy} from 'utils/zeebeClient';
import {navigateToApp} from '@pages/UtilitiesPage';
import {sleep} from 'utils/sleep';

test.beforeAll(async () => {
  await deploy([
    './resources/user_process.bpmn',
    './resources/processWithStartNodeFormDeployed.bpmn',
    './resources/create_invoice.form',
  ]);
  await sleep(2000);
});

test.beforeEach(async ({page, taskListLoginPage, taskPanelPage}) => {
  await navigateToApp(page, 'tasklist');
  await taskListLoginPage.login('demo', 'demo');
  await expect(taskPanelPage.taskListPageBanner).toBeVisible();
});

test.describe('process page', () => {
  test('process page navigation', async ({
    tasklistHeader,
    page,
    tasklistProcessesPage,
  }) => {
    await tasklistHeader.processesTab.click();
    await expect(page).toHaveURL('/tasklist/processes');
    await expect(page.getByText('Start your process on demand')).toBeVisible();
    await tasklistProcessesPage.cancelButton.click();
    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await tasklistHeader.processesTab.click();
    await tasklistProcessesPage.continueButton.click();
    await expect(page.getByText('Search processes')).toBeVisible();
  });

  test('process searching', async ({
    page,
    tasklistHeader,
    tasklistProcessesPage,
  }) => {
    await tasklistHeader.processesTab.click();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPage.continueButton.click();

    await tasklistProcessesPage.searchForProcess('fake_process');
    await expect(
      page.getByText('We could not find any process with that name'),
    ).toBeVisible();

    await tasklistProcessesPage.searchForProcess('User_Process');
    await expect(
      page.getByText('We could not find any process with that name'),
    ).toBeHidden();

    await expect(tasklistProcessesPage.processTile).toHaveCount(1);
    await expect(tasklistProcessesPage.processTile).toContainText(
      'User_Process',
    );
  });

  test('start process instance', async ({
    page,
    tasklistHeader,
    tasklistProcessesPage,
    taskPanelPage,
  }) => {
    await tasklistHeader.processesTab.click();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPage.continueButton.click();

    await tasklistProcessesPage.searchForProcess('User_Process');
    await expect(tasklistProcessesPage.processTile).toHaveCount(1, {
      timeout: 10000,
    });

    await tasklistProcessesPage.startProcessButton.click();
    await expect(page.getByText('Process has started')).toBeVisible();
    await expect(tasklistProcessesPage.startProcessButton).toBeHidden();
    await expect(page.getByText('Waiting for tasks...')).toBeVisible();
    await expect(taskPanelPage.assignToMeButton).toBeVisible({timeout: 60000});
  });

  test('complete task started by process instance', async ({
    page,
    tasklistHeader,
    tasklistProcessesPage,
    taskPanelPage,
  }) => {
    await tasklistHeader.processesTab.click();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPage.continueButton.click();

    await tasklistProcessesPage.searchForProcess('User_Process');
    await expect(tasklistProcessesPage.processTile).toHaveCount(1, {
      timeout: 10000,
    });

    await tasklistProcessesPage.startProcessButton.click();
    await tasklistProcessesPage.tasksTab.click();

    await taskPanelPage.openTask('User_Task');

    await taskPanelPage.assignToMeButton.click();
    await taskPanelPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();
  });

  test('complete process with start node having deployed form', async ({
    page,
    tasklistHeader,
    taskDetailsPage,
    tasklistProcessesPage,
    taskPanelPage,
  }) => {
    await tasklistHeader.processesTab.click();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPage.continueButton.click();

    await tasklistProcessesPage.searchForProcess(
      'processWithStartNodeFormDeployed',
    );
    await expect(tasklistProcessesPage.processTile).toHaveCount(1, {
      timeout: 30000,
    });

    await tasklistProcessesPage.startProcessButton.click();
    await page.getByRole('textbox', {name: 'Client Name'}).fill('Jon');
    await page.getByRole('textbox', {name: 'Client Address'}).fill('Earth');
    await taskDetailsPage.fillDatetimeField('Invoice Date', '1/1/3000');
    await taskDetailsPage.fillDatetimeField('Due Date', '1/2/3000');
    await taskDetailsPage.selectDropdownOption(
      'USD - United States Dollar',
      'EUR - Euro',
    );
    await page.getByRole('textbox', {name: 'Invoice Number'}).fill('123');
    await page.getByRole('button', {name: /add new/i}).click();

    await taskDetailsPage.forEachDynamicListItem(
      page.getByLabel('Item Name*'),
      async (element, index) => {
        await element.fill(`Laptop${index + 1}`);
      },
    );

    await taskDetailsPage.forEachDynamicListItem(
      page.getByLabel('Unit Price*'),
      async (element, index) => {
        await element.fill(`${index + 11}`);
      },
    );

    await taskDetailsPage.forEachDynamicListItem(
      page.getByLabel('Quantity*'),
      async (element, index) => {
        await element.clear();
        await element.fill(`${index + 21}`);
      },
    );

    await expect(page.getByText('EUR 231')).toBeVisible();
    await expect(page.getByText('EUR 264')).toBeVisible();
    await expect(page.getByText('Total: EUR 544.5')).toBeVisible();
    await tasklistProcessesPage.clickStartProcessButton(
      'processWithStartNodeFormDeployed',
    );

    await tasklistProcessesPage.tasksTab.click();
    await taskPanelPage.openTask('processStartedByForm_user_task');
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
    await taskPanelPage.assignToMeButton.click();
    await taskPanelPage.completeTaskButton.click();
    await expect(page.getByText('Task completed')).toBeVisible();
  });
});
