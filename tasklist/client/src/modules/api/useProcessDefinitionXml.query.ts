/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'modules/api';
import {type RequestError, request} from 'modules/api/request';

const HTTP_STATUS_FORBIDDEN = 403;
const HTTP_STATUS_NOT_FOUND = 404;

function useProcessDefinitionXml(
  processDefinitionKey: string,
  options?: {enabled?: boolean},
) {
  return useQuery<string, RequestError>({
    queryKey: ['processDefinitionXml', processDefinitionKey],
    queryFn: async () => {
      const {response, error} = await request(
        api.getProcessDefinitionXml({processDefinitionKey}),
      );

      if (response !== null) {
        return response.text();
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

export {useProcessDefinitionXml};
