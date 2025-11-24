/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  queryProcessInstancesRequestBodySchema,
  type QueryProcessInstancesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {parseSortParamsV2} from 'modules/utils/filter';

type ProcessInstancesSearchSort = NonNullable<
  QueryProcessInstancesRequestBody['sort']
>;

const ProcessInstancesSearchSortFieldSchema =
  queryProcessInstancesRequestBodySchema.shape.sort.unwrap().unwrap()
    .shape.field;

function parseProcessInstancesSearchSort(
  search: URLSearchParams,
): ProcessInstancesSearchSort {
  return parseSortParamsV2(search, ProcessInstancesSearchSortFieldSchema, {
    field: 'startDate',
    order: 'desc',
  });
}

export {parseProcessInstancesSearchSort};
