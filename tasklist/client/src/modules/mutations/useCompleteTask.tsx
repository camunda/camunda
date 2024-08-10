/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation} from '@tanstack/react-query';
import {api} from 'modules/api';
import {RequestError, request} from 'modules/request';
import {Task, Variable} from 'modules/types';

type Payload = {
  taskId: Task['id'];
  variables: Pick<Variable, 'name' | 'value'>[];
};

function useCompleteTask() {
  return useMutation<Task, RequestError | Error, Payload>({
    mutationFn: async (payload) => {
      const {response, error} = await request(api.completeTask(payload));

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Could not complete task');
    },
  });
}

export {useCompleteTask};
