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
import {IS_VARIABLE_FILTER_V2_ENABLED} from 'modules/feature-flags';
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
  return IS_VARIABLE_FILTER_V2_ENABLED
    ? buildSmartVariableEntry(condition)
    : buildLegacyVariableEntry(condition);
}

function buildLegacyVariableEntry(condition: VariableCondition): VariableEntry {
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

function buildSmartVariableEntry(
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
