/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  queryDecisionInstancesRequestBodySchema,
  type DecisionInstanceState,
  type QueryDecisionDefinitionsRequestBody,
  type QueryDecisionInstancesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import z from 'zod';
import {formatToISO} from '../date/formatDate';
import {parseIds, parseSortParamsV2, updateFiltersSearchString} from '.';

type DecisionInstancesSearchFilter = NonNullable<
  QueryDecisionInstancesRequestBody['filter']
>;

type DecisionDefinitionsSearchFilter = NonNullable<
  QueryDecisionDefinitionsRequestBody['filter']
>;

type DecisionsFilterField = keyof DecisionsFilter;
type DecisionsFilter = z.infer<typeof DecisionsFilterSchema>;
const DecisionsFilterSchema = z
  .object({
    name: z.string().optional(),
    version: z.string().optional(),
    evaluated: z.coerce.boolean().optional(),
    failed: z.coerce.boolean().optional(),
    decisionInstanceIds: z.string().optional(),
    processInstanceId: z.string().optional(),
    evaluationDateBefore: z.string().transform(formatToISO).optional(),
    evaluationDateAfter: z.string().transform(formatToISO).optional(),
    tenant: z.string().optional(),
  })
  .catch({});

/** Parses a {@linkcode DecisionsFilter} from URL search params. */
function parseDecisionsFilter(search: URLSearchParams) {
  return DecisionsFilterSchema.parse(Object.fromEntries(search));
}

/**
 * Parses filter arguments for a decision-instances search from the given {@linkcode URLSearchParams}.
 * Returns `undefined` when no decision-instance-state filter is selected.
 */
function parseDecisionInstancesSearchFilter(
  search: URLSearchParams,
): DecisionInstancesSearchFilter | undefined {
  const filter = parseDecisionsFilter(search);
  if (!filter.failed && !filter.evaluated) {
    return;
  }

  return {
    decisionEvaluationInstanceKey:
      mapDecisionEvaluationInstanceKeyFilter(filter),
    state: mapStateFilter(filter),
    decisionDefinitionId: filter.name,
    decisionDefinitionVersion: mapDecisionDefinitionVersionFilter(filter),
    processInstanceKey: filter.processInstanceId,
    tenantId: filter.tenant === 'all' ? undefined : filter.tenant,
    evaluationDate: mapEvaluationDateFilter(filter),
  };
}

/**
 * Parses filter arguments for a decision-definitions search from the given {@linkcode URLSearchParams}.
 */
function parseDecisionDefinitionsSearchFilter(
  search: URLSearchParams,
): DecisionDefinitionsSearchFilter {
  const filter = parseDecisionsFilter(search);

  return {
    decisionDefinitionId: filter.name,
    version: mapDecisionDefinitionVersionFilter(filter),
    tenantId: filter.tenant === 'all' ? undefined : filter.tenant,
  };
}

function mapDecisionEvaluationInstanceKeyFilter(
  filter: DecisionsFilter,
): DecisionInstancesSearchFilter['decisionEvaluationInstanceKey'] {
  const keys = filter.decisionInstanceIds
    ? parseIds(filter.decisionInstanceIds)
    : [];

  if (keys.length > 0) {
    return {$in: keys};
  }
}

function mapDecisionDefinitionVersionFilter(
  filter: DecisionsFilter,
): DecisionInstancesSearchFilter['decisionDefinitionVersion'] {
  if (filter.version && filter.version !== 'all') {
    return parseInt(filter.version);
  }
}

function mapStateFilter(
  filter: DecisionsFilter,
): DecisionInstancesSearchFilter['state'] {
  const states: DecisionInstanceState[] = [];

  if (filter.evaluated) {
    states.push('EVALUATED');
  }
  if (filter.failed) {
    states.push('FAILED');
  }

  return {$in: states};
}

function mapEvaluationDateFilter(
  filter: DecisionsFilter,
): DecisionInstancesSearchFilter['evaluationDate'] {
  if (filter.evaluationDateBefore || filter.evaluationDateAfter) {
    return {
      $gt: filter.evaluationDateAfter,
      $lt: filter.evaluationDateBefore,
    };
  }
}

type DecisionInstancesSearchSort = NonNullable<
  QueryDecisionInstancesRequestBody['sort']
>;

const DecisionInstancesSearchSortFieldSchema =
  queryDecisionInstancesRequestBodySchema.shape.sort.unwrap().unwrap()
    .shape.field;

function parseDecisionInstancesSearchSort(
  search: URLSearchParams,
): DecisionInstancesSearchSort {
  return parseSortParamsV2(search, DecisionInstancesSearchSortFieldSchema, {
    field: 'evaluationDate',
    order: 'desc',
  });
}

const DECISION_INSTANCE_FILTER_FIELDS = z.keyof(DecisionsFilterSchema.unwrap())
  .def.values;

const BOOLEAN_DECISION_INSTANCE_FILTER_FIELDS = z.keyof(
  DecisionsFilterSchema.unwrap().pick({failed: true, evaluated: true}),
).def.values;

function updateDecisionsFilterSearchString(
  currentSearch: URLSearchParams,
  newFilters: DecisionsFilter,
) {
  return updateFiltersSearchString<DecisionsFilter>(
    currentSearch,
    newFilters,
    DECISION_INSTANCE_FILTER_FIELDS,
    BOOLEAN_DECISION_INSTANCE_FILTER_FIELDS,
  );
}

export {
  parseDecisionInstancesSearchFilter,
  parseDecisionDefinitionsSearchFilter,
  parseDecisionsFilter,
  parseDecisionInstancesSearchSort,
  updateDecisionsFilterSearchString,
};
export type {DecisionsFilter, DecisionsFilterField};
