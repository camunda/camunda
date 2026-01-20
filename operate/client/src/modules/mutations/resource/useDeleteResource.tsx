/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, type UseMutationOptions} from '@tanstack/react-query';
import {deleteResource} from 'modules/api/v2/resource/deleteResource';
import {type DeleteResourceRequestBody} from '@camunda/camunda-api-zod-schemas/8.9';

function useDeleteResource(
  resourceKey: string,
  request: DeleteResourceRequestBody,
  options?: Pick<
    UseMutationOptions<Response, {status: number; statusText: string}>,
    'onSuccess' | 'onError'
  >,
) {
  return useMutation<Response, {status: number; statusText: string}, void>({
    mutationFn: async () => {
      const response = await deleteResource(resourceKey, request);
      if (!response.ok) {
        throw {status: response.status, statusText: response.statusText};
      }

      return response;
    },
    ...options,
  });
}

export {useDeleteResource};
