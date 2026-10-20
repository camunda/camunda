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

function getTotalTokens({
	inputTokens,
	outputTokens,
	cacheReadTokenCount,
	cacheCreationTokenCount,
}: TokenCounts): number {
	return (inputTokens ?? 0) + (cacheReadTokenCount ?? 0) + (cacheCreationTokenCount ?? 0) + (outputTokens ?? 0);
}

export {getTotalTokens};
