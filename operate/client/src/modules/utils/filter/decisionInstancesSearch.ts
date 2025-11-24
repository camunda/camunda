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
  type QueryDecisionInstancesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import z from 'zod';
import {formatToISO} from '../date/formatDate';
import {parseIds, parseSortParamsV2} from '.';

type DecisionInstancesSearchFilter = NonNullable<
  QueryDecisionInstancesRequestBody['filter']
>;

type DecisionInstanceFiltersField = keyof DecisionInstanceFilters;
type DecisionInstanceFilters = z.infer<typeof DecisionInstancesFilterSchema>;
const DecisionInstancesFilterSchema = z
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

/**
 * Parses filter arguments for a decision-instances search from the given {@linkcode URLSearchParams}.
 * Returns `undefined` when no decision-instance-state filter is selected.
 */
function parseDecisionInstancesSearchFilter(
  search: URLSearchParams,
): DecisionInstancesSearchFilter | undefined {
  const filter = DecisionInstancesFilterSchema.parse(
    Object.fromEntries(search),
  );
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

function mapDecisionEvaluationInstanceKeyFilter(
  filter: DecisionInstanceFilters,
): DecisionInstancesSearchFilter['decisionEvaluationInstanceKey'] {
  const keys = filter.decisionInstanceIds
    ? parseIds(filter.decisionInstanceIds)
    : [];

  if (keys.length > 0) {
    return {$in: keys};
  }
}

function mapDecisionDefinitionVersionFilter(
  filter: DecisionInstanceFilters,
): DecisionInstancesSearchFilter['decisionDefinitionVersion'] {
  if (filter.version && filter.version !== 'all') {
    return parseInt(filter.version);
  }
}

function mapStateFilter(
  filter: DecisionInstanceFilters,
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
  filter: DecisionInstanceFilters,
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
  queryDecisionInstancesRequestBodySchema.shape.sort.def.innerType.def.element
    .shape.field;

function parseDecisionInstancesSearchSort(
  search: URLSearchParams,
): DecisionInstancesSearchSort {
  return parseSortParamsV2(search, DecisionInstancesSearchSortFieldSchema, {
    field: 'evaluationDate',
    order: 'desc',
  });
}

export {parseDecisionInstancesSearchFilter, parseDecisionInstancesSearchSort};
export type {
  DecisionInstanceFilters,
  DecisionInstanceFiltersField,
  DecisionInstancesSearchFilter,
  DecisionInstancesSearchSort,
};
