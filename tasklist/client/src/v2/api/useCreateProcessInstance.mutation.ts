/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation} from '@tanstack/react-query';
import {api} from 'v2/api';
import {request} from 'common/api/request';
import type {
  CreateProcessInstanceRequestBody,
  CreateProcessInstanceResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.9';

function useCreateProcessInstance({
  onSuccess,
}: {
  onSuccess?: (data: CreateProcessInstanceResponseBody) => void;
} = {}) {
  return useMutation({
    mutationFn: async (body: CreateProcessInstanceRequestBody) => {
      const {response, error} = await request(api.createProcessInstance(body));

      if (response !== null) {
        return response.json() as Promise<CreateProcessInstanceResponseBody>;
      }

      throw error;
    },
    onSuccess,
  });
}

export {useCreateProcessInstance};
