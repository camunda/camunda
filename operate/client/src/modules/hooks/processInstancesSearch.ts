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
import type {Variable, VariableCondition} from 'modules/stores/variableFilter';
import {MULTI_VARIABLE_FILTER} from 'modules/feature-flags';
import type {QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';

type VariableEntry = NonNullable<
  NonNullable<QueryProcessInstancesRequestBody['filter']>['variables']
>[number];

function useProcessInstancesSearchFilter(
  variable?: Variable,
  conditions?: VariableCondition[],
) {
  const [searchParams] = useSearchParams();

  return useMemo(() => {
    const filter = parseProcessInstancesSearchFilter(searchParams);

    if (MULTI_VARIABLE_FILTER) {
      if (filter && conditions && conditions.length > 0) {
        filter.variables = conditions.map(buildVariableEntry);
      }
    } else {
      if (filter && variable?.name && variable?.values) {
        const parsed = (getValidVariableValues(variable.values) ?? []).map(
          (v) => JSON.stringify(v),
        );
        if (parsed.length > 0) {
          filter.variables = [
            {
              name: variable.name,
              value: parsed.length === 1 ? parsed[0]! : {$in: parsed},
            },
          ];
        }
      }
    }

    return filter;
  }, [searchParams, variable, conditions]);
}

function buildVariableEntry(condition: VariableCondition): VariableEntry {
  switch (condition.operator) {
    case 'equals':
      return {name: condition.name, value: condition.value};
    case 'notEqual':
      return {name: condition.name, value: {$neq: condition.value}};
    case 'contains':
      return {name: condition.name, value: {$like: `*${condition.value}*`}};
    case 'oneOf':
      return {
        name: condition.name,
        value: {$in: parseOneOfValues(condition.value)},
      };
    case 'exists':
      return {name: condition.name, value: {$exists: true}};
    case 'doesNotExist':
      return {name: condition.name, value: {$exists: false}};
  }
}

function parseOneOfValues(raw: string): string[] {
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (Array.isArray(parsed)) {
      return parsed.map((v) => JSON.stringify(v));
    }
  } catch {
    // fall through to comma-split
  }
  return raw
    .split(',')
    .map((v) => v.trim())
    .filter(Boolean);
}

function useProcessInstancesSearchSort() {
  const [searchParams] = useSearchParams();

  return useMemo(
    () => parseProcessInstancesSearchSort(searchParams),
    [searchParams],
  );
}

export {
  useProcessInstancesSearchFilter,
  useProcessInstancesSearchSort,
  buildVariableEntry,
  parseOneOfValues,
};
