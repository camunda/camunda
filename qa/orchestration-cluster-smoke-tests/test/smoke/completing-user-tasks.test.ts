/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DeploymentProcessResult} from '@camunda8/orchestration-cluster-api';
import {deployProcess} from '../data/deploy.ts';
import {waitForProcessInstanceFromApp} from '../data/process-instance.ts';
import {ProcessesPage} from '../pages/tasklist/processes-page.ts';
import {TaskDetailsPage} from '../pages/tasklist/task-details-page.ts';
import {expect, test} from '../test.ts';

test.describe('Completing user-tasks', {tag: '@tasklist'}, () => {
  let process: DeploymentProcessResult;

  test.beforeAll('deploy resources', async ({camunda}) => {
    process = await deployProcess(camunda, 'signup_flow.bpmn', [
      'signup_form.form',
    ]);
  });

  test('starts and completes tasks with a form', async ({
    page,
    camunda,
    cleanup,
  }) => {
    const processesPage = new ProcessesPage(page);
    const tasklistPage = new TaskDetailsPage(page);

    await test.step('Start signup process', async () => {
      await processesPage.goto();
      await processesPage.tutorialContinueButton.click();

      const responsePromise = waitForProcessInstanceFromApp(camunda, page);
      await processesPage
        .processTile(process.processDefinitionId)
        .startButton.click();

      cleanup.use(await responsePromise);
    });
    await test.step('Claim started user-task', async () => {
      await expect(tasklistPage.taskHeader).toBeVisible();
      await expect(tasklistPage.taskHeader).toContainText('Fill signup form');
      await tasklistPage.assignTaskButton.click();

      await expect(tasklistPage.assignedToMe).toBeVisible();
    });
    await test.step('Fill task form and complete task', async () => {
      await tasklistPage.formField('Username').fill('test-user');
      await tasklistPage.formField('Email').fill('test@example.com');
      await tasklistPage.formField('Age').fill('23');

      await tasklistPage.completeButton.click();
      await expect(tasklistPage.taskCompletedToast).toBeVisible();
    });
  });
});
