/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation} from '@tanstack/react-query';
import {deleteResource} from 'modules/api/v2/resource/deleteResource';
import {type DeleteResourceRequestBody} from '@camunda/camunda-api-zod-schemas/8.9';
import type {RequestError} from 'modules/request';

function useDeleteResource(
  resourceKey: string,
  request: DeleteResourceRequestBody,
  options?: {
    onSuccess?: () => void;
    onError?: (error: RequestError) => void;
  },
) {
  return useMutation({
    mutationFn: async () => {
      const {response, error} = await deleteResource(resourceKey, request);
      if (response !== null) {
        return response;
      }

      throw error;
    },
    ...options,
  });
}

export {useDeleteResource};
