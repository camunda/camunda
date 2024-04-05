/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
