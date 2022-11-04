/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {request} from 'modules/request';

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

type OperationPayload = {
  operationType: OperationEntityType;
  variableName?: string;
  variableScopeId?: string | undefined;
  variableValue?: string;
  incidentId?: string;
};

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

async function fetchVariable(id: VariableEntity['id']) {
  return request({url: `/api/variables/${id}`});
}

export {applyBatchOperation, applyOperation, getOperation, fetchVariable};
