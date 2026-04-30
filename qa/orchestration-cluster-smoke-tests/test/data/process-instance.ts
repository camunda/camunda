/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  CamundaClient,
  ProcessDefinitionKey,
  ProcessInstanceKey,
  ProcessInstanceResult,
} from '@camunda8/orchestration-cluster-api';
import {makeDisposable, type DisposableData} from './utils/disposable-data.ts';

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

async function cleanupProcessInstance(
  camunda: CamundaClient,
  processInstanceKey: ProcessInstanceKey,
): Promise<void> {
  const instance = await camunda.getProcessInstance(
    {processInstanceKey},
    {consistency: {waitUpToMs: 5_000}},
  );
  if (instance.state === 'ACTIVE') {
    await camunda.cancelProcessInstance({processInstanceKey});
  }
  await camunda.deleteProcessInstance({processInstanceKey});
}

export {cleanupProcessInstance, createProcessInstance};
