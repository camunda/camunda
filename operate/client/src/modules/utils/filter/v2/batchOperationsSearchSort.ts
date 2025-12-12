/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  queryBatchOperationsRequestBodySchema,
  type QueryBatchOperationsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {parseSortParamsV2} from 'modules/utils/filter';

type BatchOperationsSearchSort = NonNullable<
  QueryBatchOperationsRequestBody['sort']
>;

const BatchOperationsSearchSortFieldSchema =
  queryBatchOperationsRequestBodySchema.shape.sort.unwrap().unwrap()
    .shape.field;

function parseBatchOperationsSearchSort(
  search: URLSearchParams,
): BatchOperationsSearchSort {
  return parseSortParamsV2(search, BatchOperationsSearchSortFieldSchema, {
    field: 'endDate',
    order: 'desc',
  });
}

export {parseBatchOperationsSearchSort};
