/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, type UseMutationOptions} from '@tanstack/react-query';
import type {
  CreateIncidentResolutionBatchOperationRequestBody,
  CreateIncidentResolutionBatchOperationResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {resolveProcessInstancesIncidentsBatchOperation} from 'modules/api/v2/processes/resolveProcessInstancesIncidentsBatchOperation';
import type {RequestError} from 'modules/request';

const useResolveProcessInstancesIncidentsBatchOperation = (
  options?: Partial<
    UseMutationOptions<
      CreateIncidentResolutionBatchOperationResponseBody,
      RequestError,
      CreateIncidentResolutionBatchOperationRequestBody
    >
  >,
) => {
  return useMutation({
    mutationKey: ['createProcessInstanceIncidentResolutionBatchOperation'],
    mutationFn: async (payload) => {
      const {response, error} =
        await resolveProcessInstancesIncidentsBatchOperation(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    ...options,
  });
};

export {useResolveProcessInstancesIncidentsBatchOperation};
