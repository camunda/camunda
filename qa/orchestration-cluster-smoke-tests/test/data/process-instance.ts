/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  CamundaClient,
  CreateProcessInstanceResult,
  ProcessDefinitionKey,
  ProcessInstanceKey,
  ProcessInstanceResult,
} from '@camunda8/orchestration-cluster-api';
import type {Page} from '@playwright/test';
import {makeDisposable, type DisposableData} from './utils/disposable-data.ts';

/**
 * Creates a process instance and waits until it is fully exposed.
 * Takes an optional `predicate` and will wait until the create instance fulfills it.
 */
async function createProcessInstance(
  camunda: CamundaClient,
  processDefinitionKey: ProcessDefinitionKey,
  predicate?: (instance: ProcessInstanceResult) => boolean,
): Promise<DisposableData<ProcessInstanceResult>> {
  const res = await camunda.createProcessInstance({processDefinitionKey});
  const instance = await camunda.getProcessInstance(
    {processInstanceKey: res.processInstanceKey},
    {consistency: {waitUpToMs: 15_000, predicate}},
  );

  return makeDisposable(instance, () => {
    return cleanupProcessInstance(camunda, instance.processInstanceKey);
  });
}

/** Removes a process instance. If it is still active, it will be canceled first. */
async function cleanupProcessInstance(
  camunda: CamundaClient,
  processInstanceKey: ProcessInstanceKey,
): Promise<void> {
  const instance = await camunda.getProcessInstance(
    {processInstanceKey},
    {consistency: {waitUpToMs: 5_000}},
  );
  if (instance.state === 'ACTIVE') {
    await camunda
      .cancelProcessInstance({processInstanceKey}, {retry: false})
      .catch(() => void null);
  }
  await camunda.deleteProcessInstance({processInstanceKey});
}

/** Wait for a process instance creation that is triggered by the UI. */
async function waitForProcessInstanceFromApp(
  camunda: CamundaClient,
  page: Page,
): Promise<DisposableData<CreateProcessInstanceResult>> {
  const res = await page.waitForResponse('/v2/process-instances');
  if (!res.ok()) {
    throw new Error('App failed to create process instance');
  }
  const instance = (await res.json()) as CreateProcessInstanceResult;
  return makeDisposable(instance, () => {
    return cleanupProcessInstance(camunda, instance.processInstanceKey);
  });
}

export {
  cleanupProcessInstance,
  createProcessInstance,
  waitForProcessInstanceFromApp,
};
