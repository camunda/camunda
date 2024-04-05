/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'modules/api';
import {RequestError, request} from 'modules/request';
import {Form} from 'modules/types';

function useExternalForm(bpmnProcessId: string) {
  return useQuery<Form, RequestError | Error>({
    queryKey: ['externalForm', bpmnProcessId],
    queryFn: async () => {
      const {response, error} = await request(
        api.getExternalForm(bpmnProcessId),
      );

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Could not fetch external form');
    },
    staleTime: Infinity,
    cacheTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
}

export {useExternalForm};
