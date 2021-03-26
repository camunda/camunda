/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post, get} from 'modules/request';
import {RequestFilters} from 'modules/utils/filter';

const URL = '/api/process-instances';

type BatchOperationQuery = {
  active?: boolean;
  canceled?: boolean;
  completed?: boolean;
  excludeIds: string[];
  finished?: boolean;
  ids: string[];
  incidents?: boolean;
  running?: boolean;
};

type ProcessInstancesQuery = {
  query: RequestFilters;
  sorting?: {
    sortBy: string;
    sortOrder: 'desc' | 'asc';
  };
  searchAfter?: ReadonlyArray<string>;
  searchBefore?: ReadonlyArray<string>;
  pageSize?: number;
};

type OperationPayload = {
  operationType: OperationEntityType;
  variableName?: string;
  variableScopeId?: string | undefined;
  variableValue?: string;
  incidentId?: string;
};

async function fetchProcessInstance(id: ProcessInstanceEntity['id']) {
  return get(`${URL}/${id}`);
}

async function fetchProcessInstanceIncidents(id: ProcessInstanceEntity['id']) {
  return get(`${URL}/${id}/incidents`);
}

async function fetchProcessInstances(payload: ProcessInstancesQuery) {
  return await post(`${URL}`, payload);
}

async function fetchSequenceFlows(
  processInstanceId: ProcessInstanceEntity['id']
) {
  return get(`${URL}/${processInstanceId}/sequence-flows`);
}

async function fetchGroupedProcesses() {
  return get('/api/processes/grouped');
}

async function fetchProcessCoreStatistics() {
  return get(`${URL}/core-statistics`);
}

async function fetchProcessInstancesByIds(ids: ProcessInstanceEntity['id'][]) {
  return fetchProcessInstances({
    pageSize: ids.length,
    query: {
      running: true,
      finished: true,
      active: true,
      incidents: true,
      completed: true,
      canceled: true,
      ids,
    },
  });
}

async function fetchProcessInstancesStatistics(payload: any) {
  const response = await post(`${URL}/statistics`, payload);
  return {statistics: await response.json()};
}

/**
 * @param {*} payload object with query params.
 */
async function applyBatchOperation(
  operationType: OperationEntityType,
  query: BatchOperationQuery
) {
  return post(`${URL}/batch-operation`, {operationType, query});
}

/**
 * @param {*} operationType constants specifying the operation to be applied.
 * @param {*} queries object with query params.
 */
async function applyOperation(
  instanceId: ProcessInstanceEntity['id'],
  payload: OperationPayload
) {
  return post(`${URL}/${instanceId}/operation`, payload);
}

async function fetchVariables({
  instanceId,
  scopeId,
}: {
  instanceId: ProcessInstanceEntity['id'];
  scopeId: Required<VariableEntity>['scopeId'];
}) {
  return get(`${URL}/${instanceId}/variables?scopeId=${scopeId}`);
}

export {
  fetchProcessInstances,
  fetchProcessInstance,
  fetchProcessInstanceIncidents,
  fetchSequenceFlows,
  fetchGroupedProcesses,
  fetchProcessCoreStatistics,
  fetchProcessInstancesByIds,
  fetchProcessInstancesStatistics,
  applyBatchOperation,
  applyOperation,
  fetchVariables,
};
