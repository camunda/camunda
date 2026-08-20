/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, type UseMutationOptions} from '@tanstack/react-query';
import type {
  ResumeProcessInstancesBatchOperationRequestBody,
  ResumeProcessInstancesBatchOperationResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {resumeProcessInstancesBatchOperation} from 'modules/api/v2/processInstances/resumeProcessInstancesBatchOperation';
import type {RequestError} from 'modules/request';

const useResumeProcessInstancesBatchOperation = (
  options?: Partial<
    UseMutationOptions<
      ResumeProcessInstancesBatchOperationResponseBody,
      RequestError,
      ResumeProcessInstancesBatchOperationRequestBody
    >
  >,
) => {
  return useMutation({
    mutationKey: ['resumeProcessInstancesBatchOperation'],
    mutationFn: async (payload) => {
      const {response, error} =
        await resumeProcessInstancesBatchOperation(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    ...options,
  });
};

export {useResumeProcessInstancesBatchOperation};
