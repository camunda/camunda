/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.8';
import {taskHistory, type TaskHistoryOperation} from 'v2/mocks/taskHistory';

function getUseTaskHistoryQueryKey(userTaskKey: UserTask['userTaskKey']) {
  return ['task-history', userTaskKey];
}

function useTaskHistory(userTaskKey: UserTask['userTaskKey']) {
  return useQuery({
    queryKey: getUseTaskHistoryQueryKey(userTaskKey),
    queryFn: async (): Promise<TaskHistoryOperation[]> => {
      // Simulate API call with delay
      await new Promise((resolve) => setTimeout(resolve, 300));
      
      // Return mock data sorted by timestamp (latest first)
      return [...taskHistory].sort(
        (a, b) =>
          new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime(),
      );
    },
    placeholderData: (previousData) => previousData,
  });
}

export {useTaskHistory, getUseTaskHistoryQueryKey};
export type {TaskHistoryOperation};

