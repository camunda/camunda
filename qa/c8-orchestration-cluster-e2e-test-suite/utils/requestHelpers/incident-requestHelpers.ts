/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {APIRequestContext} from 'playwright-core';
import {expect, test} from '@playwright/test';
import {assertStatusCode, buildUrl, jsonHeaders} from '../http';
import {defaultAssertionOptions} from '../constants';
import {validateResponse} from '../../json-body-assertions';
import {createInstances, createSingleInstance} from '../zeebeClient';

const INCIDENT_SEARCH_ENDPOINT = '/incidents/search';

/**
 * Asserts no *open* incident: a resolved one stays searchable forever, so a
 * test that raises and resolves one on purpose could never assert health.
 * searchIncidentByPIK cannot express this — it waits for presence.
 */
export async function expectNoIncidents(
  request: APIRequestContext,
  processInstanceKey: string,
  assertionOptions = defaultAssertionOptions,
): Promise<void> {
  // Retried: an incident resolved moments earlier can still read as ACTIVE.
  await expect(async () => {
    const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
      headers: jsonHeaders(),
      data: {filter: {processInstanceKey, state: 'ACTIVE'}},
    });
    await assertStatusCode(res, 200);
    expect((await res.json()).items ?? []).toHaveLength(0);
  }).toPass(assertionOptions);
}

export async function searchIncidentByPIK(
  request: APIRequestContext,
  {processInstanceKey}: {processInstanceKey: string},
) {
  const localState: Record<string, unknown> = {};

  await expect(async () => {
    const res = await request.post(buildUrl(INCIDENT_SEARCH_ENDPOINT), {
      headers: jsonHeaders(),
      data: {
        page: {
          from: 0,
          limit: 10,
        },
        filter: {
          processInstanceKey,
        },
      },
    });

    await assertStatusCode(res, 200);
    await validateResponse(
      {
        path: INCIDENT_SEARCH_ENDPOINT,
        method: 'POST',
        status: '200',
      },
      res,
    );
    const json = await res.json();
    expect(json.items.length).toBeGreaterThan(0);
    localState['incidents'] = json.items;
  }).toPass(defaultAssertionOptions);

  return localState['incidents'] as {
    incidentKey: string;
    errorType: string;
    errorMessage: string;
    processDefinitionId: string;
    processDefinitionKey: string;
    processInstanceKey: string;
    elementInstanceKey: string;
    elementId: string;
    creationTime: string;
    tenantId: string;
    jobKey?: string;
  }[];
}

type CreatedIncidents = {
  processInstanceKey: string;
  incidentKeys: string[];
};

async function searchIncidentKeys(
  request: APIRequestContext,
  processInstanceKey: string,
): Promise<string[]> {
  return test.step('Search incident to get incidentKey', async () => {
    const incidents = await searchIncidentByPIK(request, {processInstanceKey});
    return incidents.map((incident) => incident.incidentKey);
  });
}

/**
 * Create Instance of one process with two incidents of the same type
 */
export async function createTwoIncidentsInOneProcess(
  request: APIRequestContext,
): Promise<CreatedIncidents> {
  const processInstanceKey =
    await test.step('Create process instance with an incident', async () => {
      const instances = await createInstances('processWithAnError', 1, 1);
      return instances[0].processInstanceKey as string;
    });
  const incidentKeys = await searchIncidentKeys(request, processInstanceKey);
  return {processInstanceKey, incidentKeys};
}

/**
 * Create two process instances with incidents of different type
 */
export async function createIncidentsInTwoProcesses(
  request: APIRequestContext,
): Promise<{processInstanceKeys: string[]; incidentKeys: string[]}> {
  const processInstanceKeys =
    await test.step('Create process instances with incidents', async () => {
      const processWithAnErrorInstance = await createSingleInstance(
        'processWithAnError',
        1,
      );
      const loanApprovalProcessInstance = await createSingleInstance(
        'loanApprovalProcess',
        1,
      );
      return [
        processWithAnErrorInstance.processInstanceKey as string,
        loanApprovalProcessInstance.processInstanceKey as string,
      ];
    });

  const incidentKeys: string[] = [];
  for (const processInstanceKey of processInstanceKeys) {
    incidentKeys.push(
      ...(await searchIncidentKeys(request, processInstanceKey)),
    );
  }
  return {processInstanceKeys, incidentKeys};
}

/**
 * Create one process instance with two incidents of different type
 */
export async function createTwoDifferentIncidentsInOneProcess(
  request: APIRequestContext,
): Promise<CreatedIncidents> {
  const processInstanceKey =
    await test.step('Create process instance with two different incidents', async () => {
      const instance = await createSingleInstance(
        'MultipleErrorTypesProcess',
        1,
      );
      return instance.processInstanceKey as string;
    });
  const incidentKeys = await searchIncidentKeys(request, processInstanceKey);
  return {processInstanceKey, incidentKeys};
}

export async function createSingleIncidentProcessInstance(
  request: APIRequestContext,
): Promise<CreatedIncidents> {
  const processInstanceKey =
    await test.step('Create process instance with single incidents', async () => {
      const instance = await createSingleInstance('singleIncidentProcess', 1);
      return instance.processInstanceKey as string;
    });
  const incidentKeys = await searchIncidentKeys(request, processInstanceKey);
  return {processInstanceKey, incidentKeys};
}

/**
 * This function creates a process instance of a process that has a job,
 * so that we can later create an incident by setting retries to 0 on that job.
 */
export async function createProcessInstanceWithAJob(): Promise<{
  processInstanceKey: string;
}> {
  return test.step('Create process instance with a job', async () => {
    const instance = await createSingleInstance('ProcessFlakyWorker', 1);
    return {processInstanceKey: instance.processInstanceKey as string};
  });
}
