/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSearchParams} from 'react-router-dom';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {buildMutationRequestBody} from 'modules/utils/buildMutationRequestBody';

/**
 * A hook that builds the request body for process instance batch operations,
 * taking into account:
 * - current search parameters from the URL
 * - selected and excluded IDs from the processInstancesSelectionStore
 * - variable filters from variableFilterStore.
 */
const useBatchOperationMutationRequestBody = () => {
  const variable = variableFilterStore.variable;
  const [searchParams] = useSearchParams();

  const {
    selectedProcessInstanceIds,
    excludedProcessInstanceIds,
    checkedRunningProcessInstanceIds,
  } = processInstancesSelectionStore;

  const includeIds =
    selectedProcessInstanceIds.length > 0
      ? checkedRunningProcessInstanceIds
      : [];

  return buildMutationRequestBody({
    searchParams,
    includeIds,
    excludeIds: excludedProcessInstanceIds,
    variableFilter: variable,
  });
};

export {useBatchOperationMutationRequestBody};
