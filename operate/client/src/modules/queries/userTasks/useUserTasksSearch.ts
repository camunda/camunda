/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {searchUserTasks} from 'modules/api/v2/userTasks/searchUserTasks';
import {QueryUserTasksRequestBody} from '@vzeta/camunda-api-zod-schemas';

const USER_TASKS_SEARCH_QUERY_KEY = 'userTasksSearch';

function getQueryKey(payload?: QueryUserTasksRequestBody) {
  return [USER_TASKS_SEARCH_QUERY_KEY, payload];
}

const useUserTasksSearch = (
  payload: QueryUserTasksRequestBody,
  options?: {enabled?: boolean},
) => {
  return useQuery({
    queryKey: getQueryKey(payload),
    queryFn: () =>
      searchUserTasks(payload).then(({response, error}) => {
        if (response !== null) return response;
        throw error;
      }),
    enabled: options?.enabled ?? true,
  });
};

export {useUserTasksSearch, USER_TASKS_SEARCH_QUERY_KEY};
