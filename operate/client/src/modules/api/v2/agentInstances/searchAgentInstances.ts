/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestWithThrow} from 'modules/request';
import type {
  SearchAgentInstancesRequestBody,
  SearchAgentInstancesResponseBody,
} from 'modules/queries/agentInstances/types';

const searchAgentInstances = async (
  payload: SearchAgentInstancesRequestBody,
  signal?: AbortSignal,
) => {
  // TODO(API): replace once @camunda/camunda-api-zod-schemas exposes the
  // agent-instances endpoint definitions.
  return requestWithThrow<SearchAgentInstancesResponseBody>({
    url: '/v2/agent-instances/search',
    method: 'POST',
    body: payload,
    signal,
  });
};

export {searchAgentInstances};
