/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useGenericQuery} from 'modules/queries/useGenericQuery';
import {
  fetchProcessInstancesStatistics,
  ProcessInstancesStatisticsDto,
  ProcessInstancesStatisticsRequest,
} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {useProcessInstanceFilters} from 'modules/hooks/useProcessInstancesFilters';

function getQueryKey(payload: ProcessInstancesStatisticsRequest) {
  return ['processInstancesStatistics', ...Object.values(payload)];
}

function useProcessInstancesStatistics<ParsedDataT>(
  payload: ProcessInstancesStatisticsRequest,
  parser: (data: ProcessInstancesStatisticsDto[]) => ParsedDataT,
  enabled?: boolean,
) {
  const filters = useProcessInstanceFilters();

  return useGenericQuery<ProcessInstancesStatisticsDto[], ParsedDataT>(
    getQueryKey(payload),
    () =>
      fetchProcessInstancesStatistics({
        ...payload,
        ...filters,
      }),
    parser,
    {
      queryKey: getQueryKey(payload),
      enabled,
    },
  );
}

export {useProcessInstancesStatistics};
