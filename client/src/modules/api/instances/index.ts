/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post, get} from 'modules/request';
import {parseFilterForRequest} from 'modules/utils/filter';
import {FILTER_SELECTION} from 'modules/constants';
import {OperationType} from 'modules/types';

const URL = '/api/workflow-instances';

type BatchOperationQuery = {
  active?: boolean;
  canceled?: boolean;
  completed?: boolean;
  excludeIds: Array<string>;
  finished?: boolean;
  ids: Array<string>;
  incidents?: boolean;
  running?: boolean;
};

type WorkflowInstancesQuery = {
  firstResult: number;
  maxResults: number;
  sorting?: {sortBy: string; sortOrder: string};
  active?: boolean;
  batchOperationId?: string;
  canceled?: boolean;
  completed?: boolean;
  endDateAfter?: string;
  endDateBefore?: string;
  finished?: boolean;
  ids?: Array<string>;
  incidents?: boolean;
  running?: boolean;
  startDateAfter?: string;
  startDateBefore?: string;
  workflowIds?: Array<string>;
  variable?: {name: string; value: string};
};

type OperationPayload = {
  operationType: OperationType;
  variableName?: string;
  variableScopeId?: string | undefined;
  variableValue?: string;
  incidentId?: string;
};

async function fetchWorkflowInstance(id: any) {
  return get(`${URL}/${id}`);
}

async function fetchWorkflowInstanceIncidents(id: any) {
  return get(`${URL}/${id}/incidents`);
}

async function fetchWorkflowInstances(options: WorkflowInstancesQuery) {
  const {firstResult, maxResults, ...payload} = options;
  const url = `${URL}?firstResult=${firstResult}&maxResults=${maxResults}`;

  return post(url, payload);
}

async function fetchSequenceFlows(workflowInstanceId: any) {
  return get(`${URL}/${workflowInstanceId}/sequence-flows`);
}

async function fetchGroupedWorkflows() {
  return get('/api/workflows/grouped');
}

async function fetchWorkflowCoreStatistics() {
  return get(`${URL}/core-statistics`);
}

async function fetchWorkflowInstancesByIds(ids: any) {
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

  return fetchWorkflowInstances(options);
}

async function fetchWorkflowInstancesBySelection(payload: any) {
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

async function fetchWorkflowInstancesStatistics(payload: any) {
  const url = `${URL}/statistics`;
  const response = await post(url, payload);
  return {statistics: await response.json()};
}

/**
 * @param {*} payload object with query params.
 */
async function applyBatchOperation(
  operationType: OperationType,
  query: BatchOperationQuery
) {
  return post(`${URL}/batch-operation`, {operationType, query});
}

/**
 * @param {*} operationType constants specifying the operation to be applied.
 * @param {*} queries object with query params.
 */
async function applyOperation(instanceId: string, payload: OperationPayload) {
  return post(`${URL}/${instanceId}/operation`, payload);
}

async function fetchVariables({instanceId, scopeId}: any) {
  // TODO: API CHANGED - tests will fail
  return get(`${URL}/${instanceId}/variables?scopeId=${scopeId}`);
}

export type {OperationPayload, BatchOperationQuery, WorkflowInstancesQuery};
export {
  fetchWorkflowInstance,
  fetchWorkflowInstanceIncidents,
  fetchWorkflowInstances,
  fetchSequenceFlows,
  fetchGroupedWorkflows,
  fetchWorkflowCoreStatistics,
  fetchWorkflowInstancesByIds,
  fetchWorkflowInstancesBySelection,
  fetchWorkflowInstancesStatistics,
  applyBatchOperation,
  applyOperation,
  fetchVariables,
};
