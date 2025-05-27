/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  QueryVariablesRequestBody,
  QueryVariablesResponseBody,
} from '@vzeta/camunda-api-zod-schemas/process-management';
import {RequestResult, requestWithThrow} from 'modules/request';

const searchVariables = async (
  payload: QueryVariablesRequestBody,
): RequestResult<QueryVariablesResponseBody> => {
  return requestWithThrow<QueryVariablesResponseBody>({
    url: endpoints.queryVariables.getUrl(),
    method: endpoints.queryVariables.method,
    body: payload,
  });
};

export {searchVariables};
