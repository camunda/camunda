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
import type {
  QueryVariablesByUserTaskResponseBody,
  UserTask,
} from '@camunda/camunda-api-zod-schemas/8.8';

type Params = {
  userTaskKey: UserTask['userTaskKey'];
  variableNames: string[];
};

type Options = {
  enabled?: boolean;
  refetchOnWindowFocus?: boolean;
  refetchOnReconnect?: boolean;
};

function useSelectedVariables(params: Params, options: Options = {}) {
  const {userTaskKey, variableNames} = params;

  return useQuery({
    ...options,
    queryKey: ['variables', userTaskKey, ...variableNames],
    queryFn: async () => {
      const {response, error} = await request(
        api.queryVariablesByUserTask({
          userTaskKey,
          filter: {
            name: {
              $in: variableNames,
            },
          },
          page: {
            limit: variableNames.length,
          },
        }),
      );

      if (response !== null) {
        const responseBody =
          (await response.json()) as QueryVariablesByUserTaskResponseBody;

        return responseBody.items;
      }

      throw error;
    },
  });
}

export {useSelectedVariables};
