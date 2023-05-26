/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useQuery, UseQueryOptions} from '@tanstack/react-query';
import {api} from 'modules/api';
import {request, RequestError} from 'modules/request';
import {Form} from 'modules/types';

type QueryData = Form | {schema: null};

function useForm(
  {id, processDefinitionKey}: Pick<Form, 'id' | 'processDefinitionKey'>,
  options: Pick<
    UseQueryOptions<QueryData, RequestError | Error>,
    'refetchOnWindowFocus' | 'refetchOnReconnect'
  > = {},
) {
  return useQuery<QueryData, RequestError | Error>({
    ...options,
    queryKey: ['form', id, processDefinitionKey],
    queryFn: async () => {
      const {response, error} = await request(
        api.getForm({id, processDefinitionKey}),
      );

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Could not fetch form');
    },
    initialData: {
      schema: null,
    },
  });
}

export {useForm};
