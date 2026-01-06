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
} from 'modules/utils/filter/v2/processInstancesSearch';
import {useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {getValidVariableValues} from 'modules/utils/filter/getValidVariableValues';
import type {Variable} from 'modules/stores/variableFilter';

function useProcessInstancesSearchFilter(variable?: Variable) {
  const [searchParams] = useSearchParams();

  return useMemo(() => {
    const filter = parseProcessInstancesSearchFilter(searchParams);

    if (filter && variable?.name && variable?.values) {
      const parsed = (getValidVariableValues(variable.values) ?? []).map((v) =>
        JSON.stringify(v),
      );
      if (parsed.length > 0) {
        filter.variables = [
          {
            name: variable?.name,
            value: parsed.length === 1 ? parsed[0]! : {$in: parsed},
          },
        ];
      }
    }

    return filter;
  }, [searchParams, variable]);
}

function useProcessInstancesSearchSort() {
  const [searchParams] = useSearchParams();

  return useMemo(
    () => parseProcessInstancesSearchSort(searchParams),
    [searchParams],
  );
}

export {useProcessInstancesSearchFilter, useProcessInstancesSearchSort};
