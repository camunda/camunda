/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery, UseQueryOptions} from '@tanstack/react-query';
import {api} from 'modules/api';
import {request, RequestError} from 'modules/request';
import {Form} from 'modules/types';

type QueryData = Form | {schema: null};

function useForm(
  {
    id,
    processDefinitionKey,
    version,
  }: Pick<Form, 'id' | 'processDefinitionKey'> & {
    version: Form['version'] | 'latest';
  },
  options: Pick<
    UseQueryOptions<QueryData, RequestError | Error>,
    'refetchOnWindowFocus' | 'refetchOnReconnect' | 'enabled'
  > = {},
) {
  return useQuery<QueryData, RequestError | Error>({
    ...options,
    queryKey: ['form', id, processDefinitionKey, version],
    queryFn: async () => {
      const {response, error} =
        version === null
          ? await request(
              api.getEmbeddedForm({
                id,
                processDefinitionKey,
              }),
            )
          : await request(
              api.getDeployedForm({
                id,
                processDefinitionKey,
                version,
              }),
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
