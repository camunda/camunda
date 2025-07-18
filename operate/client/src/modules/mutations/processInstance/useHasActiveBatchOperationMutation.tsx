/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutationState} from '@tanstack/react-query';
import {getMutationKey} from './useCreateIncidentResolutionBatchOperation';

const useHasActiveBatchOperationMutation = (processInstanceKey: string) => {
  const mutationStates = useMutationState({
    filters: {mutationKey: getMutationKey(processInstanceKey)},
  });
  return mutationStates?.some((mutation) => mutation.status === 'pending');
};

export {useHasActiveBatchOperationMutation};
