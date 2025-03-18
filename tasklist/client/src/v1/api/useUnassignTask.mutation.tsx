/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {api} from 'v1/api';
import {getUseTaskQueryKey} from 'v1/api/useTask.query';
import {request} from 'common/api/request';
import type {Task} from 'v1/api/types';
import {buildServerErrorSchema} from 'v1/api/buildServerErrorSchema';
import {z} from 'zod';

const messageResponseSchema = z.object({
  title: z.enum([
    'TASK_NOT_ASSIGNED_TO_CURRENT_USER',
    'TASK_NOT_ASSIGNED',
    'TASK_IS_NOT_ACTIVE',
    'TASK_PROCESSING_TIMEOUT',
    'INVALID_STATE',
  ]),
  detail: z.string(),
});
const unassignmentErrorSchema = buildServerErrorSchema(messageResponseSchema);
const unassignmentErrorMap = {
  taskNotAssignedToCurrentUser: 'TASK_NOT_ASSIGNED_TO_CURRENT_USER',
  taskNotAssigned: 'TASK_NOT_ASSIGNED',
  taskIsNotActive: 'TASK_IS_NOT_ACTIVE',
  taskProcessingTimeout: 'TASK_PROCESSING_TIMEOUT',
  invalidState: 'INVALID_STATE',
} as const;

interface UnassignmentError extends Error {
  name:
    | 'TASK_NOT_ASSIGNED_TO_CURRENT_USER'
    | 'TASK_NOT_ASSIGNED'
    | 'TASK_IS_NOT_ACTIVE'
    | 'TASK_PROCESSING_TIMEOUT'
    | 'INVALID_STATE'
    | 'Error';
  message: string;
  stack?: string;
}

function useUnassignTask() {
  const client = useQueryClient();
  return useMutation<Task, UnassignmentError, Task['id']>({
    mutationFn: async (taskId) => {
      const {response, error: errorResponse} = await request(
        api.v1.unassignTask(taskId),
      );

      if (response !== null) {
        return response.json();
      }

      if (errorResponse.variant === 'network-error') {
        throw new Error('Unexpected network error', {
          cause: errorResponse.networkError,
        });
      }

      const error = new Error('Failed to complete task');
      const errorResult = unassignmentErrorSchema.safeParse(
        await errorResponse.response.json(),
      );

      if (!errorResult.success) {
        throw error;
      }

      error.name = errorResult.data.message.title;

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

export {useUnassignTask, unassignmentErrorMap};
