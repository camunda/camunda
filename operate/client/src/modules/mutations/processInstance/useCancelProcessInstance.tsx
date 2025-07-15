/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, type UseMutationOptions} from '@tanstack/react-query';
import {cancelProcessInstance} from 'modules/api/v2/processInstances/cancelProcessInstance';

function useCancelProcessInstance(
  processInstanceKey: string,
  options?: Partial<UseMutationOptions>,
) {
  return useMutation({
    mutationFn: async () => {
      const response = await cancelProcessInstance(processInstanceKey);
      if (!response.ok) {
        throw new Error(response.statusText);
      }
      return response;
    },
    ...options,
  });
}

export {useCancelProcessInstance};
