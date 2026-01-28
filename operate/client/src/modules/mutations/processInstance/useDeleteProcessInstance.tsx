/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  useMutation,
  useQueryClient,
  type UseMutationOptions,
} from '@tanstack/react-query';
import {deleteProcessInstance} from 'modules/api/v2/processInstances/deleteProcessInstance';
import {fetchProcessInstance} from 'modules/api/v2/processInstances/fetchProcessInstance';
import {getProcessInstanceQueryKey} from 'modules/queries/processInstance/useProcessInstance';

function useDeleteProcessInstance(
  processInstanceKey: string,
  options?: Pick<
    UseMutationOptions<Response, Error>,
    'onSuccess' | 'onError'
  > & {
    shouldSkipResultCheck?: boolean;
  },
) {
  const queryClient = useQueryClient();
  const {shouldSkipResultCheck = true, ...mutationOptions} = options ?? {};

  return useMutation<Response, Error, void>({
    mutationFn: async () => {
      const response = await deleteProcessInstance(processInstanceKey);
      if (!response.ok) {
        throw new Error(response.statusText);
      }

      if (shouldSkipResultCheck) {
        return response;
      }

      queryClient.removeQueries({
        queryKey: getProcessInstanceQueryKey(processInstanceKey),
      });

      await queryClient.fetchQuery({
        queryKey: getProcessInstanceQueryKey(processInstanceKey),
        queryFn: async () => {
          const {response: processInstance, error} =
            await fetchProcessInstance(processInstanceKey);

          if (error) {
            if (error.response?.status === 404) {
              return null;
            }
            throw new Error(
              error.response?.statusText ?? 'Failed to verify deletion',
            );
          }

          if (processInstance) {
            throw new Error(
              'Process instance still exists, retrying verification...',
            );
          }

          return null;
        },
        retry: 30,
        retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 10000),
        staleTime: 0,
      });

      return response;
    },
    ...mutationOptions,
  });
}

export {useDeleteProcessInstance};
