/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessInstancesStatisticsDto} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {useProcessInstancesStatistics} from './useProcessInstancesStatistics';
import {getInstancesCount} from 'modules/utils/statistics/processInstances';

function instancesCountParser(
  flowNodeId?: string,
): (data: ProcessInstancesStatisticsDto[]) => number {
  return (data: ProcessInstancesStatisticsDto[]) => {
    return getInstancesCount(data, flowNodeId);
  };
}

function useInstancesCount(flowNodeId?: string) {
  return useProcessInstancesStatistics<number>(
    {flowNodeId: flowNodeId},
    instancesCountParser(flowNodeId),
    !!flowNodeId,
  );
}

export {useInstancesCount};
