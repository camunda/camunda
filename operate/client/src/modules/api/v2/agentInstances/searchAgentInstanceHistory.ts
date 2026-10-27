/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type QueryAgentInstanceHistoryRequestBody,
  type QueryAgentInstanceHistoryResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {requestWithThrow} from 'modules/request';

const searchAgentInstanceHistory = async (
  agentInstanceKey: string,
  payload: QueryAgentInstanceHistoryRequestBody,
  signal?: AbortSignal,
) => {
  return requestWithThrow<QueryAgentInstanceHistoryResponseBody>({
    url: endpoints.queryAgentInstanceHistory.getUrl({agentInstanceKey}),
    method: endpoints.queryAgentInstanceHistory.method,
    body: payload,
    signal,
  });
};

export {searchAgentInstanceHistory};
