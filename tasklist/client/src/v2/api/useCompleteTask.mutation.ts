/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {api} from 'v2/api';
import {request} from 'common/api/request';
import {getUseTaskQueryKey} from './useTask.query';
import {USE_TASKS_QUERY_KEY} from './useTasks.query';

function useCompleteTask() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (payload: Parameters<typeof api.completeTask>[0]) => {
      const {response, error} = await request(api.completeTask(payload));

      if (response !== null) {
        client.invalidateQueries({
          queryKey: getUseTaskQueryKey(payload.userTaskKey),
        });
        client.invalidateQueries({queryKey: [USE_TASKS_QUERY_KEY]});

        return null;
      }

      throw error;
    },
  });
}

export {useCompleteTask};
