/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery, type UseQueryResult} from '@tanstack/react-query';
import type {RequestError} from 'modules/request';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {fetchProcessInstanceDeprecated} from 'modules/api/processInstances/fetchProcessInstance';
import type {ProcessInstanceEntity} from 'modules/types/operate';

const PROCESS_INSTANCE_DEPRECATED_QUERY_KEY = 'processInstanceDeprecated';

function getQueryKey(processInstanceKey?: string) {
  return [PROCESS_INSTANCE_DEPRECATED_QUERY_KEY, processInstanceKey];
}

const useProcessInstanceDeprecated = <T = ProcessInstanceEntity>(
  select?: (data: ProcessInstanceEntity) => T,
  enabled: boolean = true,
): UseQueryResult<T, RequestError> => {
  const {processInstanceId} = useProcessInstancePageParams();

  return useQuery({
    queryKey: getQueryKey(processInstanceId),
    queryFn: processInstanceId
      ? async () => {
          const {response, error} =
            await fetchProcessInstanceDeprecated(processInstanceId);

          if (response !== null) {
            return response;
          }

          throw error;
        }
      : skipToken,
    select,
    refetchInterval: 5000,
    enabled,
  });
};

export {PROCESS_INSTANCE_DEPRECATED_QUERY_KEY, useProcessInstanceDeprecated};
