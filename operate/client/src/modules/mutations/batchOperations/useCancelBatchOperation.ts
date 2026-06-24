/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {cancelBatchOperation} from 'modules/api/v2/batchOperations/cancelBatchOperation';
import {queryKeys} from 'modules/queries/queryKeys';
import type {RequestError} from 'modules/request';

type CancelBatchOperationOptions = {
  onError?: (error: RequestError) => Promise<unknown> | unknown;
  onSuccess?: () => Promise<unknown> | unknown;
};

function useCancelBatchOperation(options?: CancelBatchOperationOptions) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (batchOperationKey: string) => {
      const {response, error} = await cancelBatchOperation(batchOperationKey);

      if (error !== null) {
        throw error;
      }

      return response;
    },
    onSuccess: (_, batchOperationKey) => {
      queryClient.invalidateQueries({
        queryKey: queryKeys.batchOperations.get(batchOperationKey),
      });
      options?.onSuccess?.();
    },
    onError: options?.onError,
  });
}

export {useCancelBatchOperation};
