/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation} from '@tanstack/react-query';
import {cancelProcessInstance} from 'modules/api/v2/processInstances/cancelProcessInstance';

function useCancelProcessInstance(
  processInstanceKey: string,
  onError?: (error: Error) => void,
) {
  return useMutation({
    mutationFn: () => cancelProcessInstance(processInstanceKey),
    onSuccess: (response) => {
      if (!response.ok) {
        throw new Error(response.statusText);
      }
    },
    onError,
  });
}

export {useCancelProcessInstance};
