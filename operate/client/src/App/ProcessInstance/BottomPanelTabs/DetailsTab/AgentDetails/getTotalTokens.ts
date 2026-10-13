/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type TokenCounts = {
  inputTokens?: number | null;
  outputTokens?: number | null;
  cacheReadTokens?: number | null;
  cacheCreationTokens?: number | null;
};

function getTotalTokens({
  inputTokens,
  outputTokens,
  cacheReadTokens,
  cacheCreationTokens,
}: TokenCounts): number {
  return (
    (inputTokens ?? 0) +
    (cacheReadTokens ?? 0) +
    (cacheCreationTokens ?? 0) +
    (outputTokens ?? 0)
  );
}

const TOTAL_TOKENS_DESCRIPTION =
  'Input + cache read + cache write + output tokens. Reasoning tokens are already included in output.';

export {getTotalTokens, TOTAL_TOKENS_DESCRIPTION};
