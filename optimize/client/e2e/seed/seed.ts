/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {isDeepStrictEqual} from 'node:util';
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

const FINAL_STATES = {
  completed: 'COMPLETED',
  running: 'ACTIVE',
  canceled: 'TERMINATED',
  open: 'ACTIVE',
  resolved: 'COMPLETED',
} as const;

function expectedInstanceStates(): Record<string, number> {
  return tally([
    ...ORDER_PROCESS.instances.map(({outcome}) => `${ORDER_PROCESS.key}:${FINAL_STATES[outcome]}`),
    ...INCIDENT_PROCESS.instances.map(
      ({incident}) => `${INCIDENT_PROCESS.key}:${FINAL_STATES[incident]}`
    ),
  ]);
}

async function readInstanceStates(camunda: Camunda): Promise<Record<string, number>> {
  const states: string[] = [];
  for (const key of [ORDER_PROCESS.key, INCIDENT_PROCESS.key]) {
    const {items} = await camunda.searchProcessInstances(
      {filter: {processDefinitionId: ProcessDefinitionId.assumeExists(key)}, page: {limit: 100}},
      {consistency: {waitUpToMs: 0}}
    );
    states.push(...items.map(({state}) => `${key}:${state}`));
  }
  return tally(states);
}

function tally(values: string[]): Record<string, number> {
  const counts: Record<string, number> = {};
  for (const value of values) {
    counts[value] = (counts[value] ?? 0) + 1;
  }
  return counts;
}

export async function ensureSeeded(camunda: Camunda): Promise<void> {
  const actual = await readInstanceStates(camunda);
  if (Object.keys(actual).length === 0) {
    await seed(camunda);
    return;
  }
  // Seeding again on top of partial data would duplicate instances, so ask for a clean stack.
  if (!isDeepStrictEqual(actual, expectedInstanceStates())) {
    throw new Error(
      `The stack holds incomplete seed data (${JSON.stringify(actual)}), e.g. from an ` +
        'interrupted run. Reset it with `docker compose down -v` and run again.'
    );
  }
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
    // The 8.8 SDK types every search result field as optional.
    const {incidentKey} = first(items, 'incident');
    if (!incidentKey) {
      throw new Error('The incident search returned no incident key');
    }
    await camunda.resolveIncident({incidentKey});
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

function first<T>(items: T[] | undefined, what: string): T {
  if (!items?.[0]) {
    throw new Error(`Expected a ${what} but found none`);
  }
  return items[0];
}
