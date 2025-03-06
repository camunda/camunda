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
import type {Process} from 'v1/api/types';

const HTTP_STATUS_FORBIDDEN = 403;
const HTTP_STATUS_NOT_FOUND = 404;

type Data = Process;

function useProcessDefinition(
  processDefinitionId: string,
  options?: {enabled?: boolean},
) {
  return useQuery<Data, RequestError>({
    queryKey: ['processDefinition', processDefinitionId],
    queryFn: async () => {
      const {response, error} = await request(
        api.v1.getProcess({processDefinitionId: processDefinitionId!}),
      );

      if (response !== null) {
        return response.json();
      }

      throw error;
    },
    retry: (failureCount, error) => {
      if (failureCount >= 3) {
        return false;
      }
      if (error.variant === 'failed-response') {
        const {status} = error.response;
        return (
          status !== HTTP_STATUS_FORBIDDEN && status !== HTTP_STATUS_NOT_FOUND
        );
      }
      return true;
    },
    enabled: options?.enabled ?? true,
    refetchOnReconnect: false,
    refetchOnWindowFocus: false,
  });
}

export {useProcessDefinition};
