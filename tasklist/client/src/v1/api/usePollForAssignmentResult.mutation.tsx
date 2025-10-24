/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {api} from 'v1/api';
import {request} from 'common/api/request';
import type {Task} from 'v1/api/types';
import {USE_TASKS_QUERY_KEY} from './useTasks.query';
import {getUseTaskQueryKey} from './useTask.query';

function usePollForAssignmentResult() {
  const client = useQueryClient();

  return useMutation({
    mutationFn: async ({
      taskId,
      wasAssigned,
    }: {
      taskId: string;
      wasAssigned: boolean;
    }) => {
      const {response, error} = await request(api.getTask(taskId));

      if (error !== null) {
        throw error;
      }

      const task = (await response.json()) as Task;

      if (wasAssigned && task.assignee !== null) {
        throw new Error('Task is assigned');
      }

      if (!wasAssigned && task.assignee === null) {
        throw new Error('Task is not assigned');
      }

      return task;
    },
    onSuccess: (task) => {
      client.setQueryData(getUseTaskQueryKey(task.id), task);
      client.invalidateQueries({queryKey: [USE_TASKS_QUERY_KEY]});
    },
    gcTime: 0,
    retry: 12,
    retryDelay: 500,
  });
}

export {usePollForAssignmentResult};
