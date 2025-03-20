/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ProcessInstancesStatisticsDto,
  ProcessInstancesStatisticsRequest,
} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {useProcessInstancesStatisticsOptions} from './useProcessInstancesStatistics';
import {useQuery} from '@tanstack/react-query';
import {
  getInstancesCount,
  getProcessInstanceKey,
} from 'modules/utils/statistics/processInstances';

function instancesCountParser(
  flowNodeId?: string,
): (data: ProcessInstancesStatisticsDto[]) => number {
  return (data: ProcessInstancesStatisticsDto[]) => {
    return getInstancesCount(data, flowNodeId);
  };
}

function useInstancesCount(
  payload: ProcessInstancesStatisticsRequest,
  flowNodeId?: string,
) {
  const processInstanceKey = getProcessInstanceKey();

  return useQuery(
    useProcessInstancesStatisticsOptions<number>(
      {
        ...payload,
        processInstanceKey,
      },
      instancesCountParser(flowNodeId),
      !!flowNodeId,
    ),
  );
}

export {useInstancesCount};
