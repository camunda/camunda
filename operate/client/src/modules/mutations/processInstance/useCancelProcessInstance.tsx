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
import {cancelProcessInstance} from 'modules/api/v2/processInstances/cancelProcessInstance';
import {fetchProcessInstance} from 'modules/api/v2/processInstances/fetchProcessInstance';
import {getProcessInstanceQueryKey} from 'modules/queries/processInstance/useProcessInstance';

function useCancelProcessInstance(
  processInstanceKey: string,
  options?: Partial<UseMutationOptions>,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      const response = await cancelProcessInstance(processInstanceKey);
      if (!response.ok) {
        throw new Error(response.statusText);
      }

      await queryClient.fetchQuery({
        queryKey: getProcessInstanceQueryKey(processInstanceKey),
        queryFn: async () => {
          const {response: processInstance, error} =
            await fetchProcessInstance(processInstanceKey);

          if (error) {
            throw new Error(error.response?.statusText);
          }

          if (processInstance.state === 'ACTIVE') {
            throw new Error('Process instance is still running');
          }

          return processInstance;
        },
        retry: true,
      });

      return response;
    },
    ...options,
  });
}

export {useCancelProcessInstance};
