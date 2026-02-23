/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstancesStatisticsOptions} from './useProcessInstancesStatistics';
import {useQuery} from '@tanstack/react-query';
import {getInstancesCount} from 'modules/utils/statistics/processInstances';
import type {
  GetProcessDefinitionStatisticsRequestBody,
  GetProcessDefinitionStatisticsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';

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
  return useQuery(
    useProcessInstancesStatisticsOptions<number>(
      payload,
      instancesCountParser(flowNodeId),
      processDefinitionKey,
      !!flowNodeId,
    ),
  );
}

export {useInstancesCount};
