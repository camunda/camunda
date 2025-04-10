/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  GetProcessDefinitionStatisticsRequestBody,
  GetProcessDefinitionStatisticsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/operate';
import {RequestResult, requestWithThrow} from 'modules/request';

enum ProcessInstanceState {
  ACTIVE = 'ACTIVE',
  COMPLETED = 'COMPLETED',
  TERMINATED = 'TERMINATED',
}

const fetchProcessInstancesStatistics = async (
  payload: GetProcessDefinitionStatisticsRequestBody,
  processDefinitionKey: string,
): RequestResult<GetProcessDefinitionStatisticsResponseBody> => {
  return requestWithThrow<GetProcessDefinitionStatisticsResponseBody>({
    url: endpoints.getProcessDefinitionStatistics.getUrl({
      processDefinitionId: processDefinitionKey,
      statisticName: 'flownode-instances',
    }),
    method: endpoints.getProcessDefinitionStatistics.method,
    body: payload,
  });
};

export {fetchProcessInstancesStatistics, ProcessInstanceState};
