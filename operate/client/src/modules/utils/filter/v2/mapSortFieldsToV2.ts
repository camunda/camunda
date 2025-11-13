/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {getSortParams} from 'modules/utils/filter';

const VALID_SORTABLE_FIELDS = new Set([
  'processInstanceKey',
  'processDefinitionName',
  'processDefinitionVersion',
  'startDate',
  'tenantId',
  'parentProcessInstanceKey',
]);

type SortItem = NonNullable<QueryProcessInstancesRequestBody['sort']>[number];
type SortField = SortItem['field'];
type SortOrder = SortItem['order'];

const DEFAULT_SORT: SortItem = {
  field: 'startDate',
  order: 'desc',
};

const normalizeSortOrder = (
  order: string | undefined,
): SortOrder | undefined => {
  if (order === 'asc' || order === 'desc') {
    return order;
  }
  return undefined;
};

const getSortFromUrl = (
  urlSearch?: string,
): QueryProcessInstancesRequestBody['sort'] => {
  const sortParams = getSortParams(urlSearch);

  if (!sortParams) {
    return [DEFAULT_SORT];
  }

  const field = VALID_SORTABLE_FIELDS.has(sortParams.sortBy)
    ? (sortParams.sortBy as SortField)
    : DEFAULT_SORT.field;

  const order = normalizeSortOrder(sortParams.sortOrder) ?? DEFAULT_SORT.order;

  return [{field, order}];
};

export {getSortFromUrl, VALID_SORTABLE_FIELDS, DEFAULT_SORT};
