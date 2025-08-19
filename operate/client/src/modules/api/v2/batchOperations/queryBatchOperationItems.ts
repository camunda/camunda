/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type QueryBatchOperationItemsRequestBody,
  type QueryBatchOperationItemsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {requestWithThrow} from 'modules/request';

const queryBatchOperationItems = async (
  payload: QueryBatchOperationItemsRequestBody,
) => {
  return requestWithThrow<QueryBatchOperationItemsResponseBody>({
    url: endpoints.queryBatchOperationItems.getUrl(),
    method: endpoints.queryBatchOperationItems.method,
    body: payload,
  });
};

export {queryBatchOperationItems};
