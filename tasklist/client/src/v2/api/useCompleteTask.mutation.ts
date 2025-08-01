/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {api} from 'v2/api';
import {request} from 'common/api/request';
import {getUseTaskQueryKey} from './useTask.query';
import {USE_TASKS_QUERY_KEY} from './useTasks.query';
import type {UserTask} from '@vzeta/camunda-api-zod-schemas/8.8';

function useCompleteTask() {
  const client = useQueryClient();

  function refetchTask(userTaskKey: string) {
    return client.fetchQuery({
      queryKey: getUseTaskQueryKey(userTaskKey),
      queryFn: async () => {
        const {response, error} = await request(api.getTask({userTaskKey}));

        if (response === null) {
          throw error;
        }

        const task = (await response.json()) as UserTask;

        if (task.state === 'COMPLETED') {
          return task;
        }

        throw new Error('Task is not completed');
      },
      retry: true,
      retryDelay: 1000,
    });
  }

  return useMutation({
    mutationFn: async (payload: Parameters<typeof api.completeTask>[0]) => {
      const {error} = await request(api.completeTask(payload));

      if (error !== null) {
        const task = await refetchTask(payload.userTaskKey);

        return task;
      }

      client.invalidateQueries({queryKey: [USE_TASKS_QUERY_KEY]});

      const task = await refetchTask(payload.userTaskKey);

      return task;
    },
  });
}

export {useCompleteTask};
