/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  parseProcessInstancesSearchFilter,
  parseProcessInstancesSearchSort,
  convertVariableConditionsToApiFormat,
} from 'modules/utils/filter/v2/processInstancesSearch';
import {useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import type {VariableCondition} from 'modules/stores/variableFilter';

function useProcessInstancesSearchFilter(conditions?: VariableCondition[]) {
  const [searchParams] = useSearchParams();

  // Serialize conditions for stable dependency comparison
  const conditionsKey = conditions ? JSON.stringify(conditions) : '';

  return useMemo(() => {
    const filter = parseProcessInstancesSearchFilter(searchParams);

    if (!filter) {
      return filter;
    }

    if (conditions && conditions.length > 0) {
      const apiVariables = convertVariableConditionsToApiFormat(conditions);
      if (apiVariables.length > 0) {
        filter.variables = apiVariables;
      }
    }

    return filter;
  }, [searchParams, conditionsKey]);
}

function useProcessInstancesSearchSort() {
  const [searchParams] = useSearchParams();

  return useMemo(
    () => parseProcessInstancesSearchSort(searchParams),
    [searchParams],
  );
}

export {useProcessInstancesSearchFilter, useProcessInstancesSearchSort};
