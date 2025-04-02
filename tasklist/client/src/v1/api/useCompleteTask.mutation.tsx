/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation} from '@tanstack/react-query';
import {api} from 'v1/api';
import {request} from 'common/api/request';
import type {Task, Variable} from 'v1/api/types';
import {buildServerErrorSchema} from 'v1/api/buildServerErrorSchema';
import {z} from 'zod';

const messageResponseSchema = z.object({
  title: z.enum([
    'TASK_NOT_ASSIGNED',
    'TASK_NOT_ASSIGNED_TO_CURRENT_USER',
    'TASK_IS_NOT_ACTIVE',
    'INVALID_STATE',
    'TASK_PROCESSING_TIMEOUT',
  ]),
  detail: z.string(),
});
const completionErrorSchema = buildServerErrorSchema(messageResponseSchema);
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
  return useMutation<Task, CompletionError, Payload>({
    mutationFn: async (payload) => {
      const {response, error: errorResponse} = await request(
        api.completeTask(payload),
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
      const errorResult = completionErrorSchema.safeParse(
        await errorResponse.response.json(),
      );

      if (!errorResult.success) {
        throw error;
      }

      error.name = errorResult.data.message.title;

      throw error;
    },
  });
}

export {useCompleteTask, completionErrorMap};
export type {CompletionError};
