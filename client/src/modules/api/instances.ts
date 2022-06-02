/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {request} from 'modules/request';
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

type VariablePayload = {
  pageSize: number;
  scopeId: string;
  searchAfter?: ReadonlyArray<string>;
  searchAfterOrEqual?: ReadonlyArray<string>;
  searchBefore?: ReadonlyArray<string>;
  searchBeforeOrEqual?: ReadonlyArray<string>;
};

async function fetchProcessInstance(id: ProcessInstanceEntity['id']) {
  return request({url: `${URL}/${id}`});
}

async function fetchProcessInstanceIncidents(id: ProcessInstanceEntity['id']) {
  return request({url: `${URL}/${id}/incidents`});
}

async function fetchProcessInstances({
  payload,
  signal,
}: {
  payload: ProcessInstancesQuery;
  signal?: AbortSignal;
}) {
  return request({
    url: `${URL}`,
    method: 'POST',
    body: payload,
    signal,
  });
}

async function fetchSequenceFlows(
  processInstanceId: ProcessInstanceEntity['id']
) {
  return request({url: `${URL}/${processInstanceId}/sequence-flows`});
}

async function fetchGroupedProcesses() {
  return request({url: '/api/processes/grouped'});
}

async function fetchProcessCoreStatistics() {
  return request({url: `${URL}/core-statistics`});
}

async function fetchProcessInstancesByIds({
  ids,
  signal,
}: {
  ids: ProcessInstanceEntity['id'][];
  signal?: AbortSignal;
}) {
  return fetchProcessInstances({
    payload: {
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
    },
    signal,
  });
}

async function fetchProcessInstancesStatistics(payload: any) {
  return request({
    url: `${URL}/statistics`,
    method: 'POST',
    body: payload,
  });
}

/**
 * @param {*} payload object with query params.
 */
async function applyBatchOperation(
  operationType: OperationEntityType,
  query: BatchOperationQuery
) {
  return request({
    url: `${URL}/batch-operation`,
    method: 'POST',
    body: {
      operationType,
      query,
    },
  });
}

/**
 * @param {*} operationType constants specifying the operation to be applied.
 * @param {*} queries object with query params.
 */
async function applyOperation(
  instanceId: ProcessInstanceEntity['id'],
  payload: OperationPayload
) {
  return request({
    url: `${URL}/${instanceId}/operation`,
    method: 'POST',
    body: payload,
  });
}

async function getOperation(batchOperationId: string) {
  return request({url: `/api/operations?batchOperationId=${batchOperationId}`});
}

async function fetchVariables({
  instanceId,
  payload,
  signal,
}: {
  instanceId: ProcessInstanceEntity['id'];
  payload: VariablePayload;
  signal?: AbortSignal;
}) {
  return request({
    url: `${URL}/${instanceId}/variables`,
    method: 'POST',
    body: payload,
    signal,
  });
}

async function fetchVariable(id: VariableEntity['id']) {
  return request({url: `/api/variables/${id}`});
}

export type {VariablePayload};
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
  getOperation,
  fetchVariables,
  fetchVariable,
};
