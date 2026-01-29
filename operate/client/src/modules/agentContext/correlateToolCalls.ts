/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentContextToolCall, AgentContextToolCallResult} from './types';

type Params = {
  toolCalls: AgentContextToolCall[];
  results: AgentContextToolCallResult[];
};

type Correlation = {
  call: AgentContextToolCall;
  result?: AgentContextToolCallResult;
};

function correlateToolCalls(params: Params): Correlation[] {
  const {toolCalls, results} = params;

  // Use the last occurrence per id (latest result wins)
  const resultById = new Map<string, AgentContextToolCallResult>();
  results.forEach((r) => {
    resultById.set(r.id, r);
  });

  return toolCalls.map((call) => {
    return {
      call,
      result: resultById.get(call.id),
    };
  });
}

export {correlateToolCalls};
