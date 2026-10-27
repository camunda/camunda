/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import type {
  ProcessInstance,
  ProcessInstanceState,
} from '@camunda/camunda-api-zod-schemas/8.10';
import type {RequestError} from 'modules/request';
import {fetchProcessInstance} from 'modules/api/v2/processInstances/fetchProcessInstance';
import {queryKeys} from 'modules/queries/queryKeys';

type StateChangeError = RequestError | Error;
type StateChangeRequest = (
  processInstanceKey: ProcessInstance['processInstanceKey'],
) => Promise<
  {response: null; error: null} | {response: null; error: RequestError}
>;

type Options = {
  onSuccess?: () => void;
  onError?: (error: StateChangeError) => void;
};

const useChangeProcessInstanceState = (
  processInstanceKey: ProcessInstance['processInstanceKey'],
  expectedState: ProcessInstanceState,
  request: StateChangeRequest,
  options?: Options,
) => {
  const queryClient = useQueryClient();

  return useMutation<null, StateChangeError>({
    mutationFn: async () => {
      const {response, error} = await request(processInstanceKey);

      if (error !== null) {
        throw error;
      }

      const processInstanceQueryKey =
        queryKeys.processInstance.get(processInstanceKey);
      const stateChangeQueryKey = [
        'processInstanceStateChange',
        processInstanceKey,
        expectedState,
      ];
      const processInstance = await queryClient.fetchQuery({
        queryKey: stateChangeQueryKey,
        queryFn: async () => {
          const {response: processInstance, error: fetchError} =
            await fetchProcessInstance(processInstanceKey);

          if (fetchError !== null) {
            throw fetchError;
          }

          const hasReachedExpectedState =
            expectedState === 'ACTIVE'
              ? processInstance.state !== 'SUSPENDED'
              : processInstance.state === expectedState;

          if (!hasReachedExpectedState) {
            throw new Error(
              `Process instance has not reached ${expectedState} state`,
            );
          }

          return processInstance;
        },
        retry: 30,
        retryDelay: 1000,
        staleTime: 0,
      });

      queryClient.setQueryData(processInstanceQueryKey, processInstance);
      queryClient.removeQueries({queryKey: stateChangeQueryKey, exact: true});

      return response;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.processInstances.base(),
      });
      options?.onSuccess?.();
    },
    onError: options?.onError,
  });
};

export {useChangeProcessInstanceState};
export type {StateChangeError};
