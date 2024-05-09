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
import {Variable} from 'modules/types';

function useSaveDraftVariables(taskId: string) {
  return useMutation<
    unknown,
    RequestError | Error,
    Pick<Variable, 'name' | 'value'>[]
  >({
    mutationFn: async (variables) => {
      const {error} = await request(api.saveVariables({taskId, variables}));

      if (error !== null) {
        throw error ?? new Error('Could not complete task');
      }
    },
  });
}

export {useSaveDraftVariables};
