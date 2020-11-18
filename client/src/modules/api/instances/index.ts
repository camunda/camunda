/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post, get} from 'modules/request';
import {parseFilterForRequest} from 'modules/utils/filter';
import {FILTER_SELECTION} from 'modules/constants';

const URL = '/api/workflow-instances';

export async function fetchWorkflowInstance(id: any) {
  const response = await get(`${URL}/${id}`);
  return await response.json();
}

export async function fetchWorkflowInstanceIncidents(id: any) {
  const response = await get(`${URL}/${id}/incidents`);
  return await response.json();
}

export async function fetchWorkflowInstances(options: any) {
  const {firstResult, maxResults, ...payload} = options;
  const url = `${URL}?firstResult=${firstResult}&maxResults=${maxResults}`;

  const response = await post(url, payload);
  return await response.json();
}

export async function fetchSequenceFlows(workflowInstanceId: any) {
  const response = await get(`${URL}/${workflowInstanceId}/sequence-flows`);
  return await response.json();
}

export async function fetchGroupedWorkflows() {
  try {
    const response = await get('/api/workflows/grouped');
    return await response.json();
  } catch (e) {
    return [];
  }
}

export async function fetchWorkflowCoreStatistics() {
  return get(`${URL}/core-statistics`);
}

export async function fetchWorkflowInstancesByIds(ids: any) {
  const payload = parseFilterForRequest({
    ...FILTER_SELECTION.running,
    ...FILTER_SELECTION.finished,
    ids: ids.join(','),
  });

  const options = {
    firstResult: 0,
    maxResults: ids.length,
    ...payload,
  };
  return await fetchWorkflowInstances(options);
}

export async function fetchWorkflowInstancesBySelection(payload: any) {
  let query = payload.queries[0];

  if (query.ids) {
    query = {
      ...payload.queries[0],
      running: true,
      active: true,
      canceled: true,
      completed: true,
      finished: true,
      incidents: true,
    };
  }

  const url = `${URL}?firstResult=${0}&maxResults=${10}`;
  const response = await post(url, {queries: [...payload.queries]});
  return await response.json();
}

export async function fetchWorkflowInstancesStatistics(payload: any) {
  const url = `${URL}/statistics`;
  const response = await post(url, payload);
  return {statistics: await response.json()};
}

/**
 * @param {*} payload object with query params.
 */
export async function applyBatchOperation(operationType: any, query: any) {
  const url = `${URL}/batch-operation`;
  const payload = {operationType, query};

  const response = await post(url, payload);
  return await response.json();
}

/**
 * @param {*} operationType constants specifying the operation to be applied.
 * @param {*} queries object with query params.
 */
export async function applyOperation(instanceId: any, payload: any) {
  const url = `${URL}/${instanceId}/operation`;

  const response = await post(url, payload);
  return await response.json();
}

export async function fetchVariables({instanceId, scopeId}: any) {
  // TODO: API CHANGED - tests will fail
  const response = await get(
    `${URL}/${instanceId}/variables?scopeId=${scopeId}`
  );
  return await response.json();
}
