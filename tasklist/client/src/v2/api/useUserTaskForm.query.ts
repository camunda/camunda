/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'v2/api';
import {request} from 'common/api/request';
import {type UserTask, type Form} from '@camunda/camunda-api-zod-schemas/8.9';

function useUserTaskForm(
  {userTaskKey}: Pick<UserTask, 'userTaskKey'>,
  options: {
    refetchOnWindowFocus?: boolean;
    refetchOnReconnect?: boolean;
    enabled?: boolean;
  } = {},
) {
  return useQuery({
    ...options,
    queryKey: ['form', userTaskKey],
    queryFn: async () => {
      const {response, error} = await request(
        api.getUserTaskForm({
          userTaskKey,
        }),
      );

      if (response !== null) {
        return response.json() as Promise<Form>;
      }

      throw error;
    },
  });
}

export {useUserTaskForm};
