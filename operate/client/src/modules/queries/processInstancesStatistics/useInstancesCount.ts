/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstancesStatisticsOptions} from './useProcessInstancesStatistics';
import {useQuery} from '@tanstack/react-query';
import {
  getInstancesCount,
  getProcessInstanceKey,
} from 'modules/utils/statistics/processInstances';
import {
  GetProcessDefinitionStatisticsRequestBody,
  GetProcessDefinitionStatisticsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/operate';

function instancesCountParser(
  flowNodeId?: string,
): (data: GetProcessDefinitionStatisticsResponseBody) => number {
  return (data: GetProcessDefinitionStatisticsResponseBody) => {
    return getInstancesCount(data.items, flowNodeId);
  };
}

function useInstancesCount(
  payload: GetProcessDefinitionStatisticsRequestBody,
  processDefinitionKey?: string,
  flowNodeId?: string,
) {
  const processInstanceKey = getProcessInstanceKey();
  const parsedPayload = {
    ...payload,
    filter: {
      ...payload.filter,
      processInstanceKey,
    },
  };
  return useQuery(
    useProcessInstancesStatisticsOptions<number>(
      parsedPayload,
      instancesCountParser(flowNodeId),
      processDefinitionKey,
      !!flowNodeId,
    ),
  );
}

export {useInstancesCount};
