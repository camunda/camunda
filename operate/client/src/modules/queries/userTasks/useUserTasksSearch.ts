/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {searchUserTasks} from 'modules/api/v2/userTasks/searchUserTasks';

const USER_TASKS_SEARCH_QUERY_KEY = 'userTasksSearch';

type UseUserTasksSearchParams = {
  elementInstanceKey: string;
  limit?: number;
};

const useUserTasksSearch = (
  {elementInstanceKey, limit = 1}: UseUserTasksSearchParams,
  options?: {enabled?: boolean},
) => {
  return useQuery({
    queryKey: [USER_TASKS_SEARCH_QUERY_KEY, elementInstanceKey, limit],
    queryFn: async () => {
      const {response, error} = await searchUserTasks({
        filter: {elementInstanceKey},
        page: {limit},
      });

      if (response !== null) {
        return response;
      }

      throw error;
    },
    ...options,
  });
};

export {useUserTasksSearch};
