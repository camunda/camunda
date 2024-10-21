/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery, type UseQueryOptions} from '@tanstack/react-query';
import {api} from 'modules/api';
import {request, type RequestError} from 'modules/request';
import type {Form} from '@vzeta/camunda-api-zod-schemas/tasklist';

type QueryData = Form | {schema: null};

function useForm(
  {formKey}: Pick<Form, 'formKey'>,
  options: Pick<
    UseQueryOptions<QueryData, RequestError | Error>,
    'refetchOnWindowFocus' | 'refetchOnReconnect' | 'enabled'
  > = {},
) {
  return useQuery<QueryData, RequestError | Error>({
    ...options,
    queryKey: ['form', formKey],
    queryFn: async () => {
      const {response, error} = await request(
        api.getForm({
          formKey,
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
