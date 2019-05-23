/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post, get} from 'modules/request';
import {parseFilterForRequest} from 'modules/utils/filter';
import {FILTER_SELECTION} from 'modules/constants';

const URL = '/api/workflow-instances';

export async function fetchWorkflowInstance(id) {
  const response = await get(`${URL}/${id}`);
  return await response.json();
}

export async function fetchWorkflowInstanceIncidents(id) {
  const response = await get(`${URL}/${id}/incidents`);
  return await response.json();
}

export async function fetchWorkflowInstances(options) {
  const {firstResult, maxResults, ...payload} = options;
  const url = `${URL}?firstResult=${firstResult}&maxResults=${maxResults}`;

  const response = await post(url, payload);
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
  try {
    const response = await get(`${URL}/core-statistics`);
    return {data: await response.json()};
  } catch (e) {
    return {error: e, data: {}};
  }
}

export async function fetchWorkflowInstancesByIds(ids) {
  const payload = {
    queries: [
      parseFilterForRequest({
        ...FILTER_SELECTION.running,
        ...FILTER_SELECTION.finished,
        ids: ids.join(',')
      })
    ]
  };

  const options = {
    firstResult: 0,
    maxResults: ids.length,
    ...payload
  };
  return await fetchWorkflowInstances(options);
}

export async function fetchWorkflowInstancesBySelection(payload) {
  let query = payload.queries[0];

  if (query.ids) {
    query = {
      ...payload.queries[0],
      running: true,
      active: true,
      canceled: true,
      completed: true,
      finished: true,
      incidents: true
    };
  }

  const url = `${URL}?firstResult=${0}&maxResults=${10}`;
  const response = await post(url, {queries: [...payload.queries]});
  return await response.json();
}

export async function fetchWorkflowInstancesStatistics(payload) {
  const url = `${URL}/statistics`;
  const response = await post(url, {queries: [...payload.queries]});
  return await response.json();
}

/**
 * @param {*} operationType constants specifying the operation to be applied.
 * @param {*} queries object with query params.
 */
export async function applyBatchOperation(operationType, queries) {
  const url = `${URL}/operation`;
  const payload = {operationType, queries};

  await post(url, payload);
}

/**
 * @param {*} operationType constants specifying the operation to be applied.
 * @param {*} queries object with query params.
 */
export async function applyOperation(instanceId, payload) {
  const url = `${URL}/${instanceId}/operation`;

  const response = await post(url, payload);
  return await response.json();
}

export async function fetchVariables(instanceId, scopeId) {
  const response = await get(
    `${URL}/${instanceId}/variables?scopeId=${scopeId}`
  );
  return await response.json();
}
