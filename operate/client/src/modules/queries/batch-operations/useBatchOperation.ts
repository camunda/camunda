/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {getBatchOperation} from 'modules/api/v2/batchOperations/getBatchOperation';

type UseBatchOperationOptions = {
  batchOperationKey?: string;
  onSuccess?: () => void;
  onError?: () => void;
};

function useBatchOperation({
  batchOperationKey,
  onSuccess,
  onError,
}: UseBatchOperationOptions) {
  return useQuery({
    queryKey: ['batchOperation', batchOperationKey],
    queryFn: async () => {
      if (!batchOperationKey) {
        return null;
      }

      const {response} = await getBatchOperation({batchOperationKey});

      if (response !== null) {
        if (response.state === 'COMPLETED') {
          onSuccess?.();
          return response;
        }

        if (response.state === 'FAILED') {
          onError?.();
          return response;
        }

        return response;
      }

      return null;
    },
    enabled: !!batchOperationKey,
    retry: false,
    refetchInterval: (query) => {
      const operation = query.state.data;

      if (
        !operation ||
        (operation.state !== 'COMPLETED' && operation.state !== 'FAILED')
      ) {
        return 2000;
      }
      return false;
    },
    refetchIntervalInBackground: false,
  });
}

export {useBatchOperation};
