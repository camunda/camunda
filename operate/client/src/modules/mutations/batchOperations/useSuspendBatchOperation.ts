/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {suspendBatchOperation} from 'modules/api/v2/batchOperations/suspendBatchOperation';
import {queryKeys} from 'modules/queries/queryKeys';

type SuspendBatchOperationOptions = {
  onError?: (error: {
    status: number;
    statusText: string;
  }) => Promise<unknown> | unknown;
};

function useSuspendBatchOperation(options?: SuspendBatchOperationOptions) {
  const queryClient = useQueryClient();

  return useMutation<void, {status: number; statusText: string}, string>({
    mutationFn: async (batchOperationKey: string) => {
      const response = await suspendBatchOperation(batchOperationKey);

      if (!response.ok) {
        throw {status: response.status, statusText: response.statusText};
      }
    },
    onSuccess: (_, batchOperationKey) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.batchOperations.get(batchOperationKey),
      });
    },
    onError: (error) => {
      return options?.onError?.(error);
    },
  });
}

export {useSuspendBatchOperation};
