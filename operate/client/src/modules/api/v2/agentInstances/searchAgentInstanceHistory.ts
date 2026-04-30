/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestWithThrow} from 'modules/request';
import type {
  SearchAgentInstanceHistoryRequestBody,
  SearchAgentInstanceHistoryResponseBody,
} from 'modules/queries/agentInstances/types';

const searchAgentInstanceHistory = async (
  agentInstanceKey: string,
  payload: SearchAgentInstanceHistoryRequestBody,
  signal?: AbortSignal,
) => {
  // TODO(API): replace once @camunda/camunda-api-zod-schemas exposes the
  // agent-instances endpoint definitions.
  return requestWithThrow<SearchAgentInstanceHistoryResponseBody>({
    url: `/v2/agent-instances/${agentInstanceKey}/history/search`,
    method: 'POST',
    body: payload,
    signal,
  });
};

export {searchAgentInstanceHistory};
