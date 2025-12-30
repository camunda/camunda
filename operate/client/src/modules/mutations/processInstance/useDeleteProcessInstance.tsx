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
  options?: Partial<UseMutationOptions> & {
    shouldSkipResultCheck?: boolean;
  },
) {
  const queryClient = useQueryClient();
  const {shouldSkipResultCheck, ...mutationOptions} = options ?? {};

  return useMutation({
    mutationFn: async () => {
      const response = await deleteProcessInstance(processInstanceKey);
      if (!response.ok) {
        throw new Error(response.statusText);
      }

      if (shouldSkipResultCheck) {
        return response;
      }

      // Clear the cache to ensure we don't get stale data during verification
      queryClient.removeQueries({
        queryKey: getProcessInstanceQueryKey(processInstanceKey),
      });

      // Poll to verify the process instance is actually deleted
      // This handles race conditions and eventual consistency
      await queryClient.fetchQuery({
        queryKey: getProcessInstanceQueryKey(processInstanceKey),
        queryFn: async () => {
          const {response: processInstance, error} =
            await fetchProcessInstance(processInstanceKey);

          if (error) {
            // A 404 error means the process instance was successfully deleted
            if (error.response?.status === 404) {
              return null;
            }
            // Any other error should be retried in case it's transient
            throw new Error(
              error.response?.statusText ?? 'Failed to verify deletion',
            );
          }

          // If we still get a process instance, it hasn't been deleted yet
          // Throw to trigger a retry
          if (processInstance) {
            throw new Error(
              'Process instance still exists, retrying verification...',
            );
          }

          return null;
        },
        // Retry for up to 5 minutes with exponential backoff
        // Attempts: 1s, 2s, 4s, 8s, then 10s each (total ~5 min)
        retry: 30,
        retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 10000),
        // Force fresh data - don't use cache
        staleTime: 0,
      });

      return response;
    },
    ...mutationOptions,
  });
}

export {useDeleteProcessInstance};
