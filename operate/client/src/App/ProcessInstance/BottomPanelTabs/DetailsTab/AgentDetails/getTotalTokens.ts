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
  cacheReadTokenCount?: number | null;
  cacheCreationTokenCount?: number | null;
};

/**
 * The token buckets are disjoint: input is uncached input only, and output
 * already includes reasoning tokens, so reasoning is never added.
 */
function getTotalTokens({
  inputTokens,
  outputTokens,
  cacheReadTokenCount,
  cacheCreationTokenCount,
}: TokenCounts): number {
  return (
    (inputTokens ?? 0) +
    (cacheReadTokenCount ?? 0) +
    (cacheCreationTokenCount ?? 0) +
    (outputTokens ?? 0)
  );
}

const TOTAL_TOKENS_DESCRIPTION =
  'Input + cache read + cache write + output tokens. Reasoning tokens are already included in output.';

export {getTotalTokens, TOTAL_TOKENS_DESCRIPTION};
