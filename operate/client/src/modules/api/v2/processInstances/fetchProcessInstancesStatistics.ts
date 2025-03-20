/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {RequestResult, requestWithThrow} from 'modules/request';

type ProcessInstancesStatisticsDto = {
  flowNodeId: string;
  active: number;
  canceled: number;
  incidents: number;
  completed: number;
};

enum ProcessInstanceState {
  RUNNING = 'RUNNING',
  COMPLETED = 'COMPLETED',
  CANCELED = 'CANCELED',
  INCIDENT = 'INCIDENT',
}

type ProcessInstancesStatisticsRequest = {
  groupBy?: string;
  startDate?: {
    $lt?: string;
    $gt?: string;
  };
  endDate?: {
    $lt?: string;
    $gt?: string;
  };
  processDefinitionKey?: {
    $in: string[];
  };
  processInstanceKey?: {
    $in?: string[];
    $nin?: string[];
  };
  parentProcessInstanceKey?: string;
  variables?: [
    {
      name: string;
      value: string;
    },
  ];
  tenantId?: string;
  hasRetriesLeft?: boolean;
  errorMessage?: string;
  flowNodeId?: string;
  state?: {
    $in: ProcessInstanceState[];
  };
  batchOperationKey?: string;
};

const fetchProcessInstancesStatistics = async (
  payload: ProcessInstancesStatisticsRequest,
): RequestResult<ProcessInstancesStatisticsDto[]> => {
  return requestWithThrow<ProcessInstancesStatisticsDto[]>({
    url: '/v2/process-instances/statistics',
    method: 'POST',
    body: payload,
  });
};

export {fetchProcessInstancesStatistics, ProcessInstanceState};
export type {ProcessInstancesStatisticsDto, ProcessInstancesStatisticsRequest};
