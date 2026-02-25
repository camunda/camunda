/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.9';
import {request, requestErrorSchema} from 'common/api/request';
import {notificationsStore} from 'common/notifications/notifications.store';
import {isTaskTimeoutError} from 'common/utils/taskErrorHandling';
import {api} from 'v2/api';
import {getUseTaskQueryKey} from 'v2/api/useTask.query';
import {USE_TASKS_QUERY_KEY} from 'v2/api/useTasks.query';

function useCompleteTask() {
  const client = useQueryClient();
  const {t} = useTranslation();

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

        throw new Error(t('taskErrorTaskNotCompleted'));
      },
      retry: true,
      retryDelay: 1000,
    });
  }

  return useMutation({
    mutationFn: async (params: Parameters<typeof api.completeTask>[0]) => {
      const {error} = await request(api.completeTask(params));

      if (error !== null) {
        const {data: parsedError, success} =
          requestErrorSchema.safeParse(error);

        if (success && parsedError.variant === 'failed-response') {
          if (isTaskTimeoutError(await parsedError.response.json())) {
            const currentTask = client.getQueryData<UserTask>(
              getUseTaskQueryKey(params.userTaskKey),
            );

            if (currentTask !== undefined) {
              client.setQueryData(getUseTaskQueryKey(params.userTaskKey), {
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

            return refetchTask(params.userTaskKey);
          }
        }

        throw error;
      }

      client.invalidateQueries({queryKey: [USE_TASKS_QUERY_KEY]});

      return refetchTask(params.userTaskKey);
    },
  });
}

export {useCompleteTask};
