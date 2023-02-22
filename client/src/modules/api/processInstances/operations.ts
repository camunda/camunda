/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';
import {BatchOperationDto} from '../sharedTypes';

const applyBatchOperation = async (
  operationType: OperationEntityType,
  query: {
    active?: boolean;
    canceled?: boolean;
    completed?: boolean;
    excludeIds: string[];
    finished?: boolean;
    ids: string[];
    incidents?: boolean;
    running?: boolean;
  }
) => {
  return requestAndParse<BatchOperationDto>({
    url: '/api/process-instances/batch-operation',
    method: 'POST',
    body: {
      operationType,
      query,
    },
  });
};

const applyOperation = async (
  instanceId: ProcessInstanceEntity['id'],
  payload: {
    operationType: OperationEntityType;
    variableName?: string;
    variableScopeId?: string | undefined;
    variableValue?: string;
    incidentId?: string;
  }
) => {
  return requestAndParse<BatchOperationDto>({
    url: `/api/process-instances/${instanceId}/operation`,
    method: 'POST',
    body: payload,
  });
};

export {applyBatchOperation, applyOperation};
