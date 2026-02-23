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
import {captureScreenshot, captureFailureVideo} from '@setup';
import {waitForAssertion} from 'utils/waitForAssertion';

test.beforeAll(async () => {
  await deploy([
    './resources/user_process.bpmn',
    './resources/processWithStartNodeFormDeployed.bpmn',
    './resources/create_invoice.form',
  ]);
  await sleep(2000);
});

test.describe('process page', () => {
  test.beforeEach(async ({page, loginPage, taskPanelPageV1}) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login('demo', 'demo');
    await expect(taskPanelPageV1.taskListPageBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('process page navigation', async ({
    tasklistHeaderV1,
    page,
    tasklistProcessesPageV1,
  }) => {
    await tasklistHeaderV1.clickProcessesTab();
    await expect(page).toHaveURL('/tasklist/processes');
    await expect(page.getByText('Start your process on demand')).toBeVisible();
    await tasklistProcessesPageV1.cancelButton.click();
    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await tasklistHeaderV1.clickProcessesTab();
    await tasklistProcessesPageV1.continueButton.click();
    await expect(page.getByText('Search processes')).toBeVisible();
  });

  test('process searching', async ({
    page,
    tasklistHeaderV1,
    tasklistProcessesPageV1,
  }) => {
    await tasklistHeaderV1.clickProcessesTab();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPageV1.continueButton.click();

    await tasklistProcessesPageV1.searchForProcess('fake_process');
    await expect(
      page.getByText('We could not find any process with that name'),
    ).toBeVisible();

    await tasklistProcessesPageV1.searchForProcess('User_Process');
    await expect(
      page.getByText('We could not find any process with that name'),
    ).toBeHidden();

    await expect(tasklistProcessesPageV1.processTile).toHaveCount(1);
    await expect(tasklistProcessesPageV1.processTile).toContainText(
      'User_Process',
    );
  });

  test('start process instance', async ({
    page,
    tasklistHeaderV1,
    tasklistProcessesPageV1,
    taskDetailsPageV1,
  }) => {
    await tasklistHeaderV1.clickProcessesTab();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPageV1.continueButton.click();

    await tasklistProcessesPageV1.searchForProcess('User_Process');
    await expect(tasklistProcessesPageV1.processTile).toHaveCount(1);

    await tasklistProcessesPageV1.startProcessButton.click();
    await expect(page.getByText('Process has started')).toBeVisible();
    await expect(tasklistProcessesPageV1.startProcessButton).toBeHidden();
    await expect(page.getByText('Waiting for tasks...')).toBeVisible();
    await expect(taskDetailsPageV1.assignToMeButton).toBeVisible({
      timeout: 60000,
    });
  });

  test('complete task started by process instance', async ({
    page,
    tasklistHeaderV1,
    tasklistProcessesPageV1,
    taskPanelPageV1,
    taskDetailsPageV1,
  }) => {
    await tasklistHeaderV1.clickProcessesTab();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPageV1.continueButton.click();

    await tasklistProcessesPageV1.searchForProcess('User_Process');
    await expect(tasklistProcessesPageV1.processTile).toHaveCount(1);

    await tasklistProcessesPageV1.startProcessButton.click();
    await tasklistHeaderV1.clickTasksTab();

    await waitForAssertion({
      assertion: async () => {
        await expect(
          taskPanelPageV1.availableTasks.getByText('User_Task').first(),
        ).toBeVisible();
      },
      onFailure: async () => {
        page.reload();
      },
    });

    await taskPanelPageV1.openTask('User_Task');

    await taskDetailsPageV1.clickAssignToMeButton();
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(page.getByText('Task completed')).toBeVisible();
  });

  test('complete process with start node having deployed form', async ({
    page,
    tasklistHeaderV1,
    taskDetailsPageV1,
    tasklistProcessesPageV1,
    taskPanelPageV1,
  }) => {
    test.slow();
    await tasklistHeaderV1.clickProcessesTab();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPageV1.continueButton.click();

    await tasklistProcessesPageV1.searchForProcess(
      'processWithStartNodeFormDeployed',
    );
    await expect(tasklistProcessesPageV1.processTile).toHaveCount(1, {
      timeout: 30000,
    });

    await tasklistProcessesPageV1.clickStartProcessButton(
      'processWithStartNodeFormDeployed',
    );
    await taskDetailsPageV1.fillTextInput('Client Name*', 'Jon');
    await taskDetailsPageV1.fillTextInput('Client Address*', 'Earth');
    await taskDetailsPageV1.fillDatetimeField('Invoice Date', '1/1/3000');
    await taskDetailsPageV1.fillDatetimeField('Due Date', '1/2/3000');
    await taskDetailsPageV1.selectDropdownOption(
      'USD - United States Dollar',
      'EUR - Euro',
    );
    await taskDetailsPageV1.fillTextInput('Invoice Number*', '123');
    await taskDetailsPageV1.addDynamicListRow();

    await taskDetailsPageV1.fillDynamicList('Item Name*', 'Laptop');
    await taskDetailsPageV1.fillDynamicList('Unit Price*', '1');
    await taskDetailsPageV1.fillDynamicList('Quantity*', '2');

    await expect(page.getByText('EUR 231')).toBeVisible();
    await expect(page.getByText('EUR 264')).toBeVisible();
    await expect(page.getByText('Total: EUR 544.5')).toBeVisible();
    await tasklistProcessesPageV1.clickStartProcessSubButton();

    await tasklistHeaderV1.clickTasksTab();
    await taskPanelPageV1.openTask('processStartedByForm_user_task');
    await expect(
      page.getByText('{"name":"jon","address":"earth"}'),
    ).toBeVisible({timeout: 60000});
    await expect(page.getByText('EUR')).toBeVisible();
    await expect(page.getByText('3000-01-01')).toBeVisible();
    await expect(page.getByText('3000-01-02')).toBeVisible();
    await expect(page.getByText('123')).toBeVisible();
    await expect(
      page.getByText(
        '[{"itemName":"laptop1","unitPrice":11,"quantity":21},{"itemName":"laptop2","unitPrice":12,"quantity":22}]',
      ),
    ).toBeVisible();
    await taskDetailsPageV1.clickAssignToMeButton();
    await taskDetailsPageV1.clickCompleteTaskButton();
    await expect(taskDetailsPageV1.taskCompletedBanner).toBeVisible({
      timeout: 60000,
    });
  });
});
