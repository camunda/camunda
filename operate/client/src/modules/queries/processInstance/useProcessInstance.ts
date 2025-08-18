/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery, type UseQueryResult} from '@tanstack/react-query';
import type {RequestError} from 'modules/request';
import type {ProcessInstance} from '@vzeta/camunda-api-zod-schemas/8.8';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {fetchProcessInstance} from 'modules/api/v2/processInstances/fetchProcessInstance';
import {isInstanceRunning} from 'modules/utils/instance';

const PROCESS_INSTANCE_QUERY_KEY = 'processInstance';

function getProcessInstanceQueryKey(processInstanceKey?: string) {
  return [PROCESS_INSTANCE_QUERY_KEY, processInstanceKey];
}

const useProcessInstance = <T = ProcessInstance>(
  select?: (data: ProcessInstance) => T,
): UseQueryResult<T, RequestError> => {
  const {processInstanceId} = useProcessInstancePageParams();

  return useQuery({
    queryKey: getProcessInstanceQueryKey(processInstanceId),
    queryFn: processInstanceId
      ? async () => {
          const {response, error} =
            await fetchProcessInstance(processInstanceId);

          if (response !== null) {
            return response;
          }

          throw error;
        }
      : skipToken,
    select,

    refetchInterval: (query) => {
      const processInstance = query.state.data;
      if (processInstance !== undefined && isInstanceRunning(processInstance)) {
        return 5000;
      }
    },
  });
};

export {useProcessInstance, getProcessInstanceQueryKey};
