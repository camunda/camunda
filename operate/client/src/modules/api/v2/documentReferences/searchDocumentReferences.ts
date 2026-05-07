/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type SearchDocumentReferencesRequestBody,
  type SearchDocumentReferencesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {requestWithThrow} from 'modules/request';

const searchDocumentReferences = async (
  payload: SearchDocumentReferencesRequestBody,
) => {
  return requestWithThrow<SearchDocumentReferencesResponseBody>({
    url: endpoints.searchDocumentReferences.getUrl(),
    method: endpoints.searchDocumentReferences.method,
    body: payload,
  });
};

export {searchDocumentReferences};
