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
import type {VariableCondition} from 'modules/stores/variableFilter';
import type {QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {logger} from 'modules/logger';
import {
  smartTransformValue,
  toStringFilterProperty,
} from 'modules/utils/smartTransform';

type VariableEntry = NonNullable<
  NonNullable<QueryProcessInstancesRequestBody['filter']>['variables']
>[number];

function useProcessInstancesSearchFilter(conditions?: VariableCondition[]) {
  const [searchParams] = useSearchParams();

  return useMemo(() => {
    const filter = parseProcessInstancesSearchFilter(searchParams);

    if (filter && conditions && conditions.length > 0) {
      const entries = conditions
        .map(buildVariableEntry)
        .filter((entry): entry is NonNullable<typeof entry> => entry !== null);
      if (entries.length > 0) {
        filter.variables = entries;
      }
    }

    return filter;
  }, [searchParams, conditions]);
}

function buildVariableEntry(
  condition: VariableCondition,
): VariableEntry | null {
  if (
    condition.operator === 'exists' ||
    condition.operator === 'doesNotExist'
  ) {
    return {
      name: condition.name,
      value: toStringFilterProperty(condition.operator, undefined),
    };
  }
  if (condition.operator === 'contains') {
    return {
      name: condition.name,
      value: toStringFilterProperty('contains', condition.value),
    };
  }
  let transformed: unknown;
  try {
    transformed = smartTransformValue(condition.value);
  } catch (e) {
    logger.error(
      '[buildVariableEntry] dropping unparseable variable filter condition:',
      condition,
      e,
    );
    return null;
  }
  return {
    name: condition.name,
    value: toStringFilterProperty(condition.operator, transformed),
  };
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
};
