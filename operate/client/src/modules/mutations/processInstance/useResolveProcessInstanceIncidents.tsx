/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, type UseMutationOptions} from '@tanstack/react-query';
import {resolveProcessInstanceIncidents} from 'modules/api/v2/processInstances/resolveProcessInstanceIncidents';

type Options = Omit<
  UseMutationOptions<
    Response,
    {status: number; statusText: string; message?: string},
    void,
    unknown
  >,
  'mutationFn'
>;

function useResolveProcessInstanceIncidents(
  processInstanceKey: string,
  options?: Options,
) {
  return useMutation<
    Response,
    {status: number; statusText: string; message?: string}
  >({
    ...options,
    mutationFn: async () => {
      const response =
        await resolveProcessInstanceIncidents(processInstanceKey);
      if (!response.ok) {
        throw {
          status: response.status,
          statusText: response.statusText,
          message: `Failed to resolve process instance incidents`,
        };
      }
      return response;
    },
  });
}

export {useResolveProcessInstanceIncidents};
