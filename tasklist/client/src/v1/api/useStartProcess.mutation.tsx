/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, type UseMutationOptions} from '@tanstack/react-query';
import {api} from 'v1/api';
import {request, type RequestError} from 'common/api/request';
import type {Process, ProcessInstance, Task, Variable} from 'v1/api/types';

function useStartProcess(
  options: Pick<
    UseMutationOptions<
      ProcessInstance,
      RequestError | Error,
      {
        bpmnProcessId: Process['bpmnProcessId'];
        variables?: Pick<Variable, 'name' | 'value'>[];
        tenantId?: Task['tenantId'];
      }
    >,
    'onSuccess'
  > = {},
) {
  return useMutation<
    ProcessInstance,
    RequestError | Error,
    Pick<Process, 'bpmnProcessId'> & {
      variables?: Pick<Variable, 'name' | 'value'>[];
    } & {
      tenantId?: Task['tenantId'];
    }
  >({
    ...options,
    mutationFn: async ({bpmnProcessId, variables = [], tenantId}) => {
      const {response, error} = await request(
        api.startProcess({
          bpmnProcessId,
          variables,
          tenantId,
        }),
      );

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Could not start process');
    },
  });
}

export {useStartProcess};
