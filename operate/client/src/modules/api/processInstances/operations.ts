/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';
import {BatchOperationDto} from '../sharedTypes';
import {RequestFilters} from 'modules/utils/filter';

type Modifications = {
  modification: 'MOVE_TOKEN';
  fromFlowNodeId: string;
  toFlowNodeId: string;
}[];

type BatchOperationQuery = RequestFilters | {excludeIds: string[]};

type MigrationPlan = {
  targetProcessDefinitionKey: string;
  mappingInstructions: {
    sourceElementId: string;
    targetElementId: string;
  }[];
};

type ApplyBatchOperationParams = {
  operationType: OperationEntityType;
  query: BatchOperationQuery;
  migrationPlan?: MigrationPlan;
  modifications?: Modifications;
};

const applyBatchOperation = async ({
  operationType,
  query,
  migrationPlan,
  modifications,
}: ApplyBatchOperationParams) => {
  return requestAndParse<BatchOperationDto>({
    url: '/api/process-instances/batch-operation',
    method: 'POST',
    body: {
      operationType,
      query,
      migrationPlan,
      modifications,
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
  },
) => {
  return requestAndParse<BatchOperationDto>({
    url: `/api/process-instances/${instanceId}/operation`,
    method: 'POST',
    body: payload,
  });
};

export {applyBatchOperation, applyOperation};
export type {BatchOperationQuery, MigrationPlan, Modifications};
