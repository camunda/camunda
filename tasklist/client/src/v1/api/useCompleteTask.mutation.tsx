/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {request, requestErrorSchema} from 'common/api/request';
import {notificationsStore} from 'common/notifications/notifications.store';
import {isTaskTimeoutError} from 'common/utils/taskErrorHandling';
import {api} from 'v1/api';
import type {Task, Variable} from 'v1/api/types';
import {getUseTaskQueryKey} from 'v1/api/useTask.query';

const completionErrorMap = {
  invalidState: 'INVALID_STATE',
  taskProcessingTimeout: 'TASK_PROCESSING_TIMEOUT',
  taskNotAssigned: 'TASK_NOT_ASSIGNED',
  taskNotAssignedToCurrentUser: 'TASK_NOT_ASSIGNED_TO_CURRENT_USER',
  taskIsNotActive: 'TASK_IS_NOT_ACTIVE',
} as const;

interface CompletionError extends Error {
  name:
    | 'TASK_NOT_ASSIGNED'
    | 'TASK_NOT_ASSIGNED_TO_CURRENT_USER'
    | 'TASK_IS_NOT_ACTIVE'
    | 'INVALID_STATE'
    | 'TASK_PROCESSING_TIMEOUT'
    | 'Error';
  message: string;
  stack?: string;
}

type Payload = {
  taskId: Task['id'];
  variables: Pick<Variable, 'name' | 'value'>[];
};

function useCompleteTask() {
  const client = useQueryClient();
  const {t} = useTranslation();

  function refetchTask(taskId: string) {
    return client.fetchQuery({
      queryKey: getUseTaskQueryKey(taskId),
      queryFn: async () => {
        const {response, error} = await request(api.getTask(taskId));

        if (response === null) {
          throw error;
        }

        const task = (await response.json()) as Task;

        if (task.taskState === 'COMPLETED') {
          return task;
        }

        throw new Error(t('taskErrorTaskNotCompleted'));
      },
      retry: true,
      retryDelay: 1000,
    });
  }

  return useMutation<Task, CompletionError, Payload>({
    mutationFn: async (params) => {
      const {error} = await request(api.completeTask(params));

      if (error !== null) {
        const {data: parsedError, success} =
          requestErrorSchema.safeParse(error);

        if (success && parsedError.variant === 'failed-response') {
          const errorData = await parsedError.response.json();

          if (isTaskTimeoutError(errorData)) {
            const currentTask = client.getQueryData(
              getUseTaskQueryKey(params.taskId),
            ) as Task;

            if (currentTask) {
              client.setQueryData(getUseTaskQueryKey(params.taskId), {
                ...currentTask,
                state: 'COMPLETING',
              });
            }

            notificationsStore.displayNotification({
              kind: 'info',
              title: t('taskDetailsCompletionDelayInfoTitle'),
              subtitle: t('taskDetailsCompletionDelayInfoSubtitle'),
              isDismissable: true,
            });

            const task = await refetchTask(params.taskId);

            return task;
          }
        }

        throw error;
      }

      client.invalidateQueries({queryKey: ['task']});

      const task = await refetchTask(params.taskId);

      return task;
    },
  });
}

export {useCompleteTask, completionErrorMap};
export type {CompletionError};
