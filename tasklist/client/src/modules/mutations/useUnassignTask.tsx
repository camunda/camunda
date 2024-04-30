/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {api} from 'modules/api';
import {getUseTaskQueryKey} from 'modules/queries/useTask';
import {RequestError, request} from 'modules/request';
import {Task} from 'modules/types';

function useUnassignTask() {
  const client = useQueryClient();
  return useMutation<Task, RequestError | Error, Task['id']>({
    mutationFn: async (taskId) => {
      const {response, error} = await request(api.unassignTask(taskId));

      if (response !== null) {
        return response.json();
      }

      const errorMessage =
        error.response instanceof Response
          ? (await error.response.json())?.message
          : undefined;

      throw errorMessage === undefined ? error : new Error(errorMessage);
    },
    onSettled: async (newTask, error) => {
      if (error !== null || newTask === undefined) {
        return;
      }

      await client.cancelQueries({queryKey: getUseTaskQueryKey(newTask.id)});
      client.setQueryData<Task>(
        getUseTaskQueryKey(newTask.id),
        (cachedTask) => {
          if (cachedTask === undefined) {
            return cachedTask;
          }

          return {
            ...newTask,
            formKey: cachedTask.formKey,
          };
        },
      );
    },
  });
}

export {useUnassignTask};
