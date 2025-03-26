/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  type UseQueryOptions,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import {api} from 'v1/api';
import {type RequestError, request} from 'common/api/request';
import type {Task} from 'v1/api/types';

function getUseTaskQueryKey(id: Task['id']) {
  return ['task', id];
}

function useTask(
  id: Task['id'],
  options?: Pick<
    UseQueryOptions<Task, RequestError | Error>,
    | 'enabled'
    | 'refetchOnWindowFocus'
    | 'refetchOnReconnect'
    | 'refetchInterval'
  >,
) {
  return useQuery<Task, RequestError | Error>({
    ...options,
    queryKey: getUseTaskQueryKey(id),
    queryFn: async () => {
      const {response, error} = await request(api.getTask(id));

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Could not fetch task');
    },
    placeholderData: (previousData) => previousData,
  });
}

function useRemoveFormReference(task: Task) {
  const client = useQueryClient();

  function removeFormReference() {
    client.setQueryData<Task>(getUseTaskQueryKey(task.id), (cachedTask) => {
      if (cachedTask === undefined) {
        return cachedTask;
      }

      return {
        ...cachedTask,
        formKey: null,
        isFormEmbedded: null,
        formId: null,
        formVersion: null,
      };
    });
  }

  return {removeFormReference};
}

export {useTask, useRemoveFormReference, getUseTaskQueryKey};
