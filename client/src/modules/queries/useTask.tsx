/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {UseQueryOptions, useQuery, useQueryClient} from '@tanstack/react-query';
import {api} from 'modules/api';
import {RequestError, request} from 'modules/request';
import {Task} from 'modules/types';

function getUseTaskQueryKey(id: Task['id']) {
  return ['task', id];
}

function useTask(
  id: Task['id'],
  options?: Pick<
    UseQueryOptions<Task, RequestError | Error>,
    'enabled' | 'refetchOnWindowFocus' | 'refetchOnReconnect'
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
    keepPreviousData: true,
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
      };
    });
  }

  return {removeFormReference};
}

export {useTask, useRemoveFormReference, getUseTaskQueryKey};
