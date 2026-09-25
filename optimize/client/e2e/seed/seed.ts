/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createCamundaClient, ProcessDefinitionId} from '@camunda8/orchestration-cluster-api';

import {env} from '../env';
import {INCIDENT_PROCESS, ORDER_PROCESS, type OrderInstance} from './dataset';

const RESOURCES_DIR = path.join(path.dirname(fileURLToPath(import.meta.url)), 'resources');
const CONSISTENCY = {consistency: {waitUpToMs: 30_000}};

type Camunda = ReturnType<typeof createCamundaClient>;
type ProcessInstanceKey = Awaited<
  ReturnType<Camunda['createProcessInstance']>
>['processInstanceKey'];

export function createCamunda(): Camunda {
  return createCamundaClient({
    config: {
      CAMUNDA_REST_ADDRESS: env.camundaUrl,
      CAMUNDA_AUTH_STRATEGY: 'BASIC',
      CAMUNDA_BASIC_AUTH_USERNAME: env.camundaUser,
      CAMUNDA_BASIC_AUTH_PASSWORD: env.camundaPassword,
    },
  });
}

async function isSeeded(camunda: Camunda): Promise<boolean> {
  const {items} = await camunda.searchProcessDefinitions(
    {filter: {processDefinitionId: ProcessDefinitionId.assumeExists(INCIDENT_PROCESS.key)}},
    {consistency: {waitUpToMs: 0}}
  );
  return items.length > 0;
}

export async function ensureSeeded(camunda: Camunda): Promise<void> {
  if (await isSeeded(camunda)) {
    return;
  }
  await seed(camunda);
}

async function seed(camunda: Camunda): Promise<void> {
  for (const resource of [...ORDER_PROCESS.resources, ...INCIDENT_PROCESS.resources]) {
    await camunda.deployResourcesFromFiles([path.join(RESOURCES_DIR, resource)]);
  }

  for (const instance of ORDER_PROCESS.instances) {
    await runOrder(camunda, instance);
  }

  for (const {incident} of INCIDENT_PROCESS.instances) {
    await runIncident(camunda, incident);
  }
}

async function runOrder(camunda: Camunda, order: OrderInstance) {
  const {processInstanceKey} = await camunda.createProcessInstance({
    processDefinitionId: ProcessDefinitionId.assumeExists(ORDER_PROCESS.key),
    processDefinitionVersion: order.version,
    variables: {
      amount: order.amount,
      category: order.category,
      express: order.express,
      inStock: order.inStock,
    },
  });

  await completeNextJob(camunda, 'e2e-check-stock');

  if (!order.inStock) {
    return;
  }

  const userTaskKey = await findUserTask(camunda, processInstanceKey);
  if (order.assignee) {
    await camunda.assignUserTask({userTaskKey, assignee: order.assignee});
  }

  if (order.outcome === 'canceled') {
    await camunda.cancelProcessInstance({processInstanceKey});
  } else if (order.outcome === 'completed') {
    await camunda.completeUserTask({userTaskKey});
    if (order.version === 2) {
      await completeNextJob(camunda, 'e2e-notify-customer');
    }
  }
}

async function runIncident(camunda: Camunda, incident: 'open' | 'resolved') {
  const {processInstanceKey} = await camunda.createProcessInstance({
    processDefinitionId: ProcessDefinitionId.assumeExists(INCIDENT_PROCESS.key),
  });

  const job = await activateNextJob(camunda, 'e2e-charge-card');
  await camunda.failJob({jobKey: job.jobKey, retries: 0, errorMessage: 'Card declined'});

  if (incident === 'resolved') {
    const {items} = await camunda.searchIncidents(
      {filter: {processInstanceKey, state: 'ACTIVE'}},
      CONSISTENCY
    );
    await camunda.updateJob({jobKey: job.jobKey, changeset: {retries: 1}});
    await camunda.resolveIncident({incidentKey: first(items, 'incident').incidentKey});
    await completeNextJob(camunda, 'e2e-charge-card');
  }
}

async function findUserTask(camunda: Camunda, processInstanceKey: ProcessInstanceKey) {
  const {items} = await camunda.searchUserTasks(
    {filter: {processInstanceKey, state: 'CREATED'}},
    CONSISTENCY
  );
  return first(items, 'user task').userTaskKey;
}

async function activateNextJob(camunda: Camunda, type: string) {
  for (let attempt = 0; attempt < 10; attempt++) {
    const {jobs} = await camunda.activateJobs({
      type,
      maxJobsToActivate: 1,
      timeout: 60_000,
      worker: 'optimize-e2e-seed',
      requestTimeout: 5_000,
    });
    if (jobs[0]) {
      return jobs[0];
    }
  }
  throw new Error(`No job of type "${type}" became available`);
}

async function completeNextJob(camunda: Camunda, type: string) {
  const job = await activateNextJob(camunda, type);
  await camunda.completeJob({jobKey: job.jobKey});
}

function first<T>(items: T[], what: string): T {
  if (!items[0]) {
    throw new Error(`Expected a ${what} but found none`);
  }
  return items[0];
}
