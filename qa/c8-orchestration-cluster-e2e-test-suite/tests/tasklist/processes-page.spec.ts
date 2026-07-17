/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {publicTest as test} from 'fixtures';
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

// Polls the process definition search API until the given version is indexed
// as the latest, so the Processes tab is guaranteed to reflect it.
const waitForLatestProcessVersion = async (
  processDefinitionId: string,
  expectedVersion: number,
) => {
  const baseUrl =
    process.env.ZEEBE_REST_ADDRESS ?? process.env.CORE_APPLICATION_URL;
  const authorization = `Basic ${Buffer.from(
    `${process.env.CAMUNDA_BASIC_AUTH_USERNAME}:${process.env.CAMUNDA_BASIC_AUTH_PASSWORD}`,
  ).toString('base64')}`;
  for (let attempt = 0; attempt < 30; attempt++) {
    const response = await fetch(`${baseUrl}/v2/process-definitions/search`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json', authorization},
      body: JSON.stringify({
        filter: {processDefinitionId, isLatestVersion: true},
      }),
    });
    if (response.ok) {
      const body = (await response.json()) as {
        items?: Array<{version: number}>;
      };
      if (body.items?.[0]?.version === expectedVersion) {
        return;
      }
    }
    await sleep(1000);
  }
  throw new Error(
    `Process definition ${processDefinitionId} version ${expectedVersion} was not indexed as latest in time`,
  );
};

test.describe('process page', () => {
  test.beforeEach(async ({page, loginPage, taskPanelPage}) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login('demo', 'demo');
    await expect(taskPanelPage.taskListPageBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('process page navigation', async ({
    tasklistHeader,
    page,
    tasklistProcessesPage,
  }) => {
    await tasklistHeader.clickProcessesTab();
    await expect(page).toHaveURL('/tasklist/processes');
    await expect(page.getByText('Start your process on demand')).toBeVisible();
    await tasklistProcessesPage.cancelButton.click();
    await expect(page.getByText('Welcome to Tasklist')).toBeVisible();

    await tasklistHeader.clickProcessesTab();
    await tasklistProcessesPage.continueButton.click();
    await expect(page.getByText('Search processes')).toBeVisible();
  });

  test('process searching', async ({
    page,
    tasklistHeader,
    tasklistProcessesPage,
  }) => {
    await tasklistHeader.clickProcessesTab();
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
    taskDetailsPage,
  }) => {
    await tasklistHeader.clickProcessesTab();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPage.continueButton.click();

    await tasklistProcessesPage.searchForProcess('User_Process');
    await expect(tasklistProcessesPage.processTile).toHaveCount(1);

    await tasklistProcessesPage.startProcessButton.click();
    await expect(page.getByText('Process has started')).toBeVisible();
    await expect(tasklistProcessesPage.startProcessButton).toBeHidden();
    await expect(page.getByText('Waiting for tasks...')).toBeVisible();
    await expect(taskDetailsPage.assignToMeButton).toBeVisible({
      timeout: 60000,
    });
  });

  test('complete task started by process instance', async ({
    page,
    tasklistHeader,
    tasklistProcessesPage,
    taskPanelPage,
    taskDetailsPage,
  }) => {
    await tasklistHeader.clickProcessesTab();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPage.continueButton.click();

    await tasklistProcessesPage.searchForProcess('User_Process');
    await expect(tasklistProcessesPage.processTile).toHaveCount(1);

    await tasklistProcessesPage.startProcessButton.click();
    await tasklistHeader.clickTasksTab();

    await waitForAssertion({
      assertion: async () => {
        await expect(
          taskPanelPage.availableTasks.getByText('User_Task').first(),
        ).toBeVisible();
      },
      onFailure: async () => {
        void page.reload();
      },
    });

    await taskPanelPage.openTask('User_Task');

    await taskDetailsPage.clickAssignToMeButton();
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(page.getByText('Task completed')).toBeVisible();
  });

  const NOT_FOUND_MESSAGE = 'We could not find any process with that name';

  test('filter processes by "Requires form input to start"', async ({
    page,
    tasklistHeader,
    tasklistProcessesPage,
  }) => {
    await tasklistHeader.clickProcessesTab();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPage.continueButton.click();

    await tasklistProcessesPage.filterByStartForm(
      'Requires form input to start',
    );

    // A process with a start form is shown under this filter, tagged.
    await tasklistProcessesPage.searchForProcess(
      'processWithStartNodeFormDeployed',
    );
    await expect(tasklistProcessesPage.processTile).toHaveCount(1, {
      timeout: 30000,
    });
    await expect(
      tasklistProcessesPage.requiresFormInputTagFor(
        'processWithStartNodeFormDeployed',
      ),
    ).toHaveText('Requires form input');

    // A process without a start form is excluded by this filter.
    await tasklistProcessesPage.searchForProcess('User_Process');
    await expect(page.getByText(NOT_FOUND_MESSAGE)).toBeVisible();
    await expect(tasklistProcessesPage.processTile).toHaveCount(0);
  });

  test('filter processes by "Does not require form input to start"', async ({
    page,
    tasklistHeader,
    tasklistProcessesPage,
  }) => {
    await tasklistHeader.clickProcessesTab();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPage.continueButton.click();

    await tasklistProcessesPage.filterByStartForm(
      'Does not require form input to start',
    );

    // A process without a start form is shown under this filter and not tagged.
    await tasklistProcessesPage.searchForProcess('User_Process');
    await expect(tasklistProcessesPage.processTile).toHaveCount(1, {
      timeout: 30000,
    });
    await expect(tasklistProcessesPage.processTile).toContainText(
      'User_Process',
    );
    await expect(
      tasklistProcessesPage.requiresFormInputTagFor('User_Process'),
    ).toBeHidden();

    // A process with a start form is excluded by this filter.
    await tasklistProcessesPage.searchForProcess(
      'processWithStartNodeFormDeployed',
    );
    await expect(page.getByText(NOT_FOUND_MESSAGE)).toBeVisible();
    await expect(tasklistProcessesPage.processTile).toHaveCount(0);
  });

  test('complete process with start node having deployed form', async ({
    page,
    tasklistHeader,
    taskDetailsPage,
    tasklistProcessesPage,
    taskPanelPage,
  }) => {
    test.slow();
    await tasklistHeader.clickProcessesTab();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPage.continueButton.click();

    await tasklistProcessesPage.searchForProcess(
      'processWithStartNodeFormDeployed',
    );
    await expect(tasklistProcessesPage.processTile).toHaveCount(1, {
      timeout: 30000,
    });

    await tasklistProcessesPage.clickStartProcessButton(
      'processWithStartNodeFormDeployed',
    );
    await taskDetailsPage.fillTextInput('Client Name*', 'Jon');
    await taskDetailsPage.fillTextInput('Client Address*', 'Earth');
    await taskDetailsPage.fillDatetimeField('Invoice Date', '1/1/3000');
    await taskDetailsPage.fillDatetimeField('Due Date', '1/2/3000');
    await taskDetailsPage.selectDropdownOption(
      'USD - United States Dollar',
      'EUR - Euro',
    );
    await taskDetailsPage.fillTextInput('Invoice Number*', '123');
    await taskDetailsPage.addDynamicListRow();
    await sleep(500);

    await taskDetailsPage.fillDynamicList('Item Name*', 'Laptop');
    await taskDetailsPage.fillDynamicList('Unit Price*', '1');
    await taskDetailsPage.fillDynamicList('Quantity*', '2');

    await expect(page.getByText('EUR 231')).toBeVisible();
    await expect(page.getByText('EUR 264')).toBeVisible();
    await expect(page.getByText('Total: EUR 544.5')).toBeVisible();
    await sleep(500);
    await tasklistProcessesPage.clickStartProcessSubButton();

    await tasklistHeader.clickTasksTab();
    await taskPanelPage.openTask('processStartedByForm_user_task');
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
    await taskDetailsPage.clickAssignToMeButton();
    await taskDetailsPage.clickCompleteTaskButton();
    await expect(taskDetailsPage.taskCompletedBanner).toBeVisible({
      timeout: 60000,
    });
  });

  // Regression test for https://github.com/camunda/camunda/issues/40045
  test('show only the latest version of a process definition', async ({
    page,
    tasklistHeader,
    tasklistProcessesPage,
    taskPanelPage,
  }) => {
    await deploy(['./resources/latest_version_process.form']);
    await deploy(['./resources/latest_version_process_v1.bpmn']);
    const deployment = await deploy([
      './resources/latest_version_process_v2.bpmn',
    ]);
    await waitForLatestProcessVersion(
      'Latest_Version_Process',
      deployment.processes[0].processDefinitionVersion,
    );

    await tasklistHeader.clickProcessesTab();
    await expect(page).toHaveURL('/tasklist/processes');
    await tasklistProcessesPage.continueButton.click();

    await tasklistProcessesPage.searchForProcess('Latest_Version_Process');

    await waitForAssertion({
      assertion: async () => {
        await expect(tasklistProcessesPage.processTile).toHaveCount(1);
        await expect(tasklistProcessesPage.processTile).toContainText(
          'Latest Version Process',
        );
      },
      onFailure: async () => {
        await page.reload();
        if (await tasklistProcessesPage.continueButton.isVisible()) {
          await tasklistProcessesPage.continueButton.click();
        }
        await tasklistProcessesPage.searchForProcess('Latest_Version_Process');
      },
    });

    await tasklistProcessesPage.startProcessButton.click();
    await expect(page.getByText('Process has started')).toBeVisible();

    await tasklistHeader.clickTasksTab();
    await waitForAssertion({
      assertion: async () => {
        await expect(
          taskPanelPage.availableTasks
            .getByText('Latest Version Task V2')
            .first(),
        ).toBeVisible();
      },
      onFailure: async () => {
        await page.reload();
      },
    });
    await expect(
      taskPanelPage.availableTasks.getByText('Latest Version Task V1'),
    ).toBeHidden();
  });
});
