/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type GetProcessDefinitionStatisticsRequestBody,
  type GetProcessDefinitionStatisticsResponseBody,
} from '@vzeta/camunda-api-zod-schemas';
import type {RequestResult} from 'modules/request';
import {requestWithThrow} from 'modules/request';

const ProcessInstanceState = {
  ACTIVE: 'ACTIVE',
  COMPLETED: 'COMPLETED',
  TERMINATED: 'TERMINATED',
} as const;

const fetchProcessInstancesStatistics = async (
  payload: GetProcessDefinitionStatisticsRequestBody,
  processDefinitionKey: string,
): RequestResult<GetProcessDefinitionStatisticsResponseBody> => {
  return requestWithThrow<GetProcessDefinitionStatisticsResponseBody>({
    url: endpoints.getProcessDefinitionStatistics.getUrl({
      processDefinitionKey,
      statisticName: 'element-instances',
    }),
    method: endpoints.getProcessDefinitionStatistics.method,
    body: payload,
  });
};

export {fetchProcessInstancesStatistics, ProcessInstanceState};
