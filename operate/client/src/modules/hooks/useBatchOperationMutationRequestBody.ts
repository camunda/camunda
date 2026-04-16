/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSearchParams} from 'react-router-dom';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {processInstancesSelectionStore} from 'modules/stores/instancesSelection';
import {buildMutationRequestBody} from 'modules/utils/buildMutationRequestBody';

const useBatchOperationMutationRequestBody = () => {
  const variable = variableFilterStore.variable;
  const [searchParams] = useSearchParams();

  const {selectedIds, excludedIds, checkedRunningIds} =
    processInstancesSelectionStore;

  const includeIds = selectedIds.length > 0 ? checkedRunningIds : [];

  return buildMutationRequestBody({
    searchParams,
    includeIds,
    excludeIds: excludedIds,
    variableFilter: variable,
  });
};

/**
 * Hook for building the request body for delete batch operations.
 * Unlike running operations (cancel, retry), delete operations only work on
 * finished instances (COMPLETED or TERMINATED).
 */
const useDeleteBatchOperationMutationRequestBody = () => {
  const variable = variableFilterStore.variable;
  const [searchParams] = useSearchParams();

  const {selectedIds, excludedIds, checkedFinishedIds} =
    processInstancesSelectionStore;

  const includeIds = selectedIds.length > 0 ? checkedFinishedIds : [];

  return buildMutationRequestBody({
    searchParams,
    includeIds,
    excludeIds: excludedIds,
    variableFilter: variable,
  });
};

export {
  useBatchOperationMutationRequestBody,
  useDeleteBatchOperationMutationRequestBody,
};
