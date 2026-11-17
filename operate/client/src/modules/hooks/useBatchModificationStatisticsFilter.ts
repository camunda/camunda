/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {GetProcessDefinitionStatisticsRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {useProcessInstanceStatisticsFilters} from 'modules/hooks/useProcessInstanceStatisticsFilters';
import {processInstancesSelectionStore} from 'modules/stores/instancesSelection';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {buildInstanceKeyCriterion} from 'modules/utils/instances/buildInstanceKeyCriterion';

/**
 * Combines a process instances statistics filter with a process instances selection filter.
 * Similar to {@linkcode useProcessInstanceStatisticsFilters} but includes the users process
 * instances selection as well.
 */
function useBatchModificationStatisticsFilter(): GetProcessDefinitionStatisticsRequestBody {
  const base = useProcessInstanceStatisticsFilters(
    variableFilterStore.conditions,
  );
  const {selectedIds, excludedIds} = processInstancesSelectionStore;

  const processInstanceKey = buildInstanceKeyCriterion(
    selectedIds,
    excludedIds,
  );

  return {
    filter: {
      ...base.filter,
      ...(processInstanceKey ? {processInstanceKey} : {}),
    },
  };
}

export {useBatchModificationStatisticsFilter};
