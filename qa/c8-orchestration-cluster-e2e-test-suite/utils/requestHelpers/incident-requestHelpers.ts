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

/**
 * Create Instance of one process with two incidents of the same type
 */
export async function createTwoIncidentsInOneProcess(
  localState: Record<string, unknown>,
  request: APIRequestContext,
) {
  await test.step('Create process instance with an incident', async () => {
    const instances = await createInstances('processWithAnError', 1, 1);
    localState['processInstanceKey'] = instances[0].processInstanceKey;
  });

  await test.step('Search incident to get incidentKey', async () => {
    const incidents = await searchIncidentByPIK(request, {
      processInstanceKey: localState['processInstanceKey'] as string,
    });
    localState['incidentKeys'] = incidents.map(
      (incident) => incident.incidentKey,
    );
  });
}

/**
 * Create two process instances with incidents of different type
 */
export async function createIncidentsInTwoProcesses(
  localState: Record<string, unknown>,
  request: APIRequestContext,
) {
  await test.step('Create process instances with incidents', async () => {
    const instances = [];
    const processWithAnErrorInstance = await createSingleInstance(
      'processWithAnError',
      1,
    );
    instances.push(processWithAnErrorInstance.processInstanceKey);

    const loanApprovalProcessInstance = await createSingleInstance(
      'loanApprovalProcess',
      1,
    );
    instances.push(loanApprovalProcessInstance.processInstanceKey);
    localState['processInstanceKey'] = instances;
  });

  await test.step('Search incident to get incidentKey', async () => {
    const allKeys: Array<number | string> = [];
    for (const key of localState['processInstanceKey'] as string[]) {
      const incidents = await searchIncidentByPIK(request, {
        processInstanceKey: key,
      });
      allKeys.push(...(incidents ?? []).map((i) => i.incidentKey));
    }

    localState['incidentKeys'] = allKeys;
  });
}

/**
 * Create one process instance with two incidents of different type
 */
export async function createTwoDifferentIncidentsInOneProcess(
  localState: Record<string, unknown>,
  request: APIRequestContext,
) {
  await test.step('Create process instance with two different incidents', async () => {
    const instance = await createSingleInstance('MultipleErrorTypesProcess', 1);
    localState['processInstanceKey'] = instance.processInstanceKey;
  });

  await test.step('Search incident to get incidentKey', async () => {
    const incidents = await searchIncidentByPIK(request, {
      processInstanceKey: localState['processInstanceKey'] as string,
    });
    localState['incidentKeys'] = incidents.map(
      (incident) => incident.incidentKey,
    );
  });
}

export async function createSingleIncidentProcessInstance(
  localState: Record<string, unknown>,
  request: APIRequestContext,
) {
  await test.step('Create process instance with single incidents', async () => {
    const instance = await createSingleInstance('singleIncidentProcess', 1);
    localState['processInstanceKey'] = instance.processInstanceKey;
  });

  await test.step('Search incident to get incidentKey', async () => {
    const incidents = await searchIncidentByPIK(request, {
      processInstanceKey: localState['processInstanceKey'] as string,
    });
    localState['incidentKeys'] = incidents.map(
      (incident) => incident.incidentKey,
    );
  });
}

/**
 * This function creates a process instance of a process that has a job,
 * so that we can later create an incident by setting retries to 0 on that job.
 */
export async function createProcessInstanceWithAJob(
  localState: Record<string, unknown>,
) {
  await test.step('Create process instance with a job', async () => {
    const instance = await createSingleInstance('ProcessFlakyWorker', 1);
    localState['processInstanceKey'] = instance.processInstanceKey;
  });
}
