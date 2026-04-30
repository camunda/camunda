/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DeploymentProcessResult} from '@camunda8/orchestration-cluster-api';
import {deployProcess} from '../data/deploy.ts';
import {createProcessInstance} from '../data/process-instance.ts';
import {ProcessInstancePage} from '../pages/operate/process-instance-page.ts';
import {expect, test} from '../test.ts';

test.describe('Retrying processes', {tag: '@operate'}, () => {
  let process: DeploymentProcessResult;

  test.beforeAll('Deploy resources', async ({camunda}) => {
    process = await deployProcess(camunda, 'recoverable_process.bpmn');
  });

  test('recovers a failed process instance', async ({
    camunda,
    cleanup,
    page,
  }) => {
    const instancePage = new ProcessInstancePage(page);
    const instance = cleanup.use(
      await createProcessInstance(
        camunda,
        process.processDefinitionKey,
        (instance) => instance.hasIncident,
      ),
    );

    await test.step('Open failed process instance', async () => {
      await instancePage.goto(instance.processInstanceKey);
      await instancePage.dismissUpdateNoticeButton.click();
      await expect(instancePage.stateIcon('INCIDENT')).toBeVisible();
    });
    await test.step('Repair failed process instance', async () => {
      const variable = instancePage.variablesPanel.variable('count');

      await instancePage.variablesPanel.trigger.click();
      await expect(variable.container).toBeVisible();
      await variable.editButton.click();
      await variable.editField.fill('7');
      await variable.saveButton.click();

      await expect(
        instancePage.variablesPanel.variableUpdatedToast,
      ).toBeVisible();
    });
    await test.step('Retry the process instance', async () => {
      await instancePage.retryButton.click();
      await expect(instancePage.retryScheduledToast).toBeVisible();

      await expect(instancePage.stateIcon('COMPLETED')).toBeVisible({
        timeout: 15_000,
      });
    });
  });
});
