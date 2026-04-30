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
} from 'modules/utils/filter/processInstancesSearch';
import {useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {getValidVariableValues} from 'modules/utils/filter/getValidVariableValues';
import type {Variable} from 'modules/stores/variableFilter';

function useProcessInstancesSearchFilter(variables?: Variable[]) {
  const [searchParams] = useSearchParams();

  return useMemo(() => {
    const filter = parseProcessInstancesSearchFilter(searchParams);

    if (filter && variables && variables.length > 0) {
      const variableEntries = variables
        .filter((v) => v.name && v.values)
        .flatMap((v) => {
          const parsed = (getValidVariableValues(v.values) ?? []).map((val) =>
            JSON.stringify(val),
          );
          if (parsed.length === 0) return [];
          return [
            {
              name: v.name,
              value: parsed.length === 1 ? parsed[0]! : {$in: parsed},
            },
          ];
        });

      if (variableEntries.length > 0) {
        filter.variables = variableEntries;
      }
    }

    return filter;
  }, [searchParams, variables]);
}

function useProcessInstancesSearchSort() {
  const [searchParams] = useSearchParams();

  return useMemo(
    () => parseProcessInstancesSearchSort(searchParams),
    [searchParams],
  );
}

export {useProcessInstancesSearchFilter, useProcessInstancesSearchSort};
