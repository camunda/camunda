/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSearchParams} from 'react-router-dom';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {
  processInstancesSelectionStore,
  decisionInstancesSelectionStore,
} from 'modules/stores/instancesSelection';
import {buildMutationRequestBody} from 'modules/utils/buildMutationRequestBody';
import {parseDecisionInstancesSearchFilter} from 'modules/utils/filter/decisionsFilter';
import {buildInstanceKeyCriterion} from 'modules/utils/instances/buildInstanceKeyCriterion';
import type {CreateDecisionInstancesDeletionBatchOperationRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';

const getIncludeIds = (selectedIds: string[], checkedEligibleIds: string[]) => {
  if (selectedIds.length === 0) {
    return [];
  }

  return checkedEligibleIds.length > 0 ? checkedEligibleIds : selectedIds;
};

const FINISHED_STATE_SEARCH_PARAMS = ['completed', 'canceled'];

const useProcessInstancesBatchOperationMutationRequestBody = (
  checkedEligibleIds: string[],
  includeSuspended: boolean,
  excludeFinishedStates = false,
) => {
  const conditions = variableFilterStore.conditions;
  const [searchParams] = useSearchParams();

  const {selectedIds, excludedIds} = processInstancesSelectionStore;

  const includeIds = getIncludeIds(selectedIds, checkedEligibleIds);

  const requestSearchParams = new URLSearchParams(searchParams);
  if (excludeFinishedStates) {
    FINISHED_STATE_SEARCH_PARAMS.forEach((param) =>
      requestSearchParams.delete(param),
    );
  }

  return buildMutationRequestBody({
    searchParams: requestSearchParams,
    includeIds,
    excludeIds: excludedIds,
    conditions,
    includeSuspended,
  });
};

/** Retry and batch modification only apply to active instances. */
const useBatchOperationMutationRequestBody = () =>
  useProcessInstancesBatchOperationMutationRequestBody(
    processInstancesSelectionStore.checkedRunningIds,
    false,
  );

// The cancellation endpoint rejects finished states, so they are left out of the filter.
const useCancelProcessInstancesBatchOperationMutationRequestBody = () =>
  useProcessInstancesBatchOperationMutationRequestBody(
    [
      ...processInstancesSelectionStore.checkedRunningIds,
      ...processInstancesSelectionStore.checkedSuspendedIds,
    ],
    true,
    true,
  );

const useSuspendProcessInstancesBatchOperationMutationRequestBody = () =>
  useProcessInstancesBatchOperationMutationRequestBody(
    processInstancesSelectionStore.checkedRunningIds,
    false,
  );

const useResumeProcessInstancesBatchOperationMutationRequestBody = () =>
  useProcessInstancesBatchOperationMutationRequestBody(
    processInstancesSelectionStore.checkedSuspendedIds,
    true,
  );

/**
 * Hook for building the request body for delete batch operations.
 * Unlike running operations (cancel, retry), delete operations only work on
 * finished instances (COMPLETED or TERMINATED).
 */
const useDeleteProcessInstancesBatchOperationMutationRequestBody = () => {
  const conditions = variableFilterStore.conditions;
  const [searchParams] = useSearchParams();

  const {selectedIds, excludedIds, checkedFinishedIds} =
    processInstancesSelectionStore;

  const includeIds = getIncludeIds(selectedIds, checkedFinishedIds);

  return buildMutationRequestBody({
    searchParams,
    includeIds,
    excludeIds: excludedIds,
    conditions,
  });
};

const useDeleteDecisionInstancesBatchOperationRequestBody =
  (): CreateDecisionInstancesDeletionBatchOperationRequestBody => {
    const [searchParams] = useSearchParams();

    const {selectedIds, excludedIds, checkedIds} =
      decisionInstancesSelectionStore;

    const includeIds = selectedIds.length > 0 ? checkedIds : [];
    const keyCriterion = buildInstanceKeyCriterion(includeIds, excludedIds);

    const baseFilter = parseDecisionInstancesSearchFilter(searchParams) ?? {};
    const filter = keyCriterion
      ? {...baseFilter, decisionEvaluationInstanceKey: keyCriterion}
      : baseFilter;

    return {filter};
  };

export {
  useBatchOperationMutationRequestBody,
  useCancelProcessInstancesBatchOperationMutationRequestBody,
  useSuspendProcessInstancesBatchOperationMutationRequestBody,
  useResumeProcessInstancesBatchOperationMutationRequestBody,
  useDeleteProcessInstancesBatchOperationMutationRequestBody,
  useDeleteDecisionInstancesBatchOperationRequestBody,
};
