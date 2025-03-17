/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'v1/api';
import {type RequestError, request} from 'common/api/request';
import type {Form} from 'v1/api/types';

function useExternalForm(bpmnProcessId: string) {
  return useQuery<Form, RequestError | Error>({
    queryKey: ['externalForm', bpmnProcessId],
    queryFn: async () => {
      const {response, error} = await request(
        api.v1.getExternalForm(bpmnProcessId),
      );

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Could not fetch external form');
    },
    staleTime: Infinity,
    gcTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
}

export {useExternalForm};
