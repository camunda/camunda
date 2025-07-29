/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {searchUserTasks} from 'modules/api/v2/userTasks/searchUserTasks';
import type {QueryUserTasksRequestBody} from '@vzeta/camunda-api-zod-schemas/8.8';

const USER_TASKS_BY_ELEMENT_INSTANCE_QUERY_KEY =
  'useGetUserTaskByElementInstance';

const useGetUserTaskByElementInstance = (
  elementInstanceKey: string,
  options: {enabled: boolean} = {enabled: true},
) => {
  return useQuery({
    queryKey: [USER_TASKS_BY_ELEMENT_INSTANCE_QUERY_KEY, elementInstanceKey],
    queryFn: async () => {
      const payload: QueryUserTasksRequestBody = {
        filter: {elementInstanceKey},
        page: {limit: 1},
      };

      const {response, error} = await searchUserTasks(payload);
      if (response !== null) {
        return response.items?.[0] ?? null;
      }

      throw error;
    },
    ...options,
  });
};

export {useGetUserTaskByElementInstance};
