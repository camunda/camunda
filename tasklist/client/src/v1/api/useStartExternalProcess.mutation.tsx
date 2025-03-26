/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, type UseMutationOptions} from '@tanstack/react-query';
import {api} from 'v1/api';
import {type RequestError, request} from 'common/api/request';
import type {ProcessInstance} from 'v1/api/types';

function useStartExternalProcess(
  options: Pick<
    UseMutationOptions<
      ProcessInstance,
      RequestError | Error,
      Parameters<typeof api.startExternalProcess>[0]
    >,
    'onError' | 'onSuccess' | 'onMutate'
  > = {},
) {
  return useMutation<
    ProcessInstance,
    RequestError | Error,
    Parameters<typeof api.startExternalProcess>[0]
  >({
    ...options,
    mutationFn: async (payload) => {
      const {response, error} = await request(
        api.startExternalProcess(payload),
      );

      if (response !== null) {
        return response.json();
      }

      throw error;
    },
  });
}

export {useStartExternalProcess};
