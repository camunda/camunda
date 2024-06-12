/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, UseMutationOptions} from '@tanstack/react-query';
import {api} from 'modules/api';
import {RequestError, request} from 'modules/request';
import {ProcessInstance} from 'modules/types';

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

      throw error ?? new Error('Could not start process');
    },
  });
}

export {useStartExternalProcess};
