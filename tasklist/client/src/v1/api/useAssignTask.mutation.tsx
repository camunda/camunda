/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {z} from 'zod';
import {useTranslation} from 'react-i18next';
import {request} from 'common/api/request';
import {notificationsStore} from 'common/notifications/notifications.store';
import {api} from 'v1/api';
import {getUseTaskQueryKey} from 'v1/api/useTask.query';
import type {Task} from 'v1/api/types';
import {buildServerErrorSchema} from 'v1/api/buildServerErrorSchema';

const messageResponseSchema = z.object({
  title: z.enum([
    'TASK_ALREADY_ASSIGNED',
    'TASK_IS_NOT_ACTIVE',
    'TASK_PROCESSING_TIMEOUT',
    'INVALID_STATE',
  ]),
  detail: z.string(),
});
const assignmentErrorSchema = buildServerErrorSchema(messageResponseSchema);
const assignmentErrorMap = {
  taskAlreadyAssigned: 'TASK_ALREADY_ASSIGNED',
  taskIsNotActive: 'TASK_IS_NOT_ACTIVE',
  taskProcessingTimeout: 'TASK_PROCESSING_TIMEOUT',
  invalidState: 'INVALID_STATE',
} as const;

interface AssignmentError extends Error {
  name:
    | 'TASK_ALREADY_ASSIGNED'
    | 'TASK_IS_NOT_ACTIVE'
    | 'TASK_PROCESSING_TIMEOUT'
    | 'INVALID_STATE'
    | 'Error';
  message: string;
  stack?: string;
}

function useAssignTask() {
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

        if (task.taskState === 'ASSIGNING') {
          throw new Error('Task is still assigning');
        }

        return task;
      },
      retry: true,
      retryDelay: 1000,
    });
  }

  return useMutation<Task, AssignmentError, Task['id']>({
    mutationFn: async (taskId) => {
      const {response, error: errorResponse} = await request(
        api.assignTask(taskId),
      );

      if (response !== null) {
        return response.json();
      }

      if (errorResponse.variant === 'network-error') {
        throw new Error('Unexpected network error', {
          cause: errorResponse.networkError,
        });
      }

      const error = new Error('Failed to assign task');
      const errorResult = assignmentErrorSchema.safeParse(
        await errorResponse.response.json(),
      );

      if (!errorResult.success) {
        throw error;
      }

      error.name = errorResult.data.message.title;

      if (error.name === assignmentErrorMap.taskProcessingTimeout) {
        const currentTask = client.getQueryData(
          getUseTaskQueryKey(taskId),
        ) as Task;
        if (currentTask) {
          client.setQueryData(getUseTaskQueryKey(taskId), {
            ...currentTask,
            taskState: 'ASSIGNING' as const,
            assignee: null,
          });
        }

        notificationsStore.displayNotification({
          kind: 'info',
          title: t('taskDetailsAssignmentDelayInfoTitle'),
          subtitle: t('taskDetailsAssignmentDelayInfoSubtitle'),
          isDismissable: true,
        });

        const task = await refetchTask(taskId);
        return task;
      }

      throw error;
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

export {useAssignTask, assignmentErrorMap};
