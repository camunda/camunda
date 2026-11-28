/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const BATCH_MODIFICATION_LOCKED_FILTER_KEYS = ['process', 'version', 'elementId'] as const;

type BatchModificationLockedFilterKey = (typeof BATCH_MODIFICATION_LOCKED_FILTER_KEYS)[number];
type BatchModificationLockedFilters<TValue> = Record<BatchModificationLockedFilterKey, TValue>;

function mapBatchModificationLockedFilters<TValue>(
	mapper: (key: BatchModificationLockedFilterKey) => TValue,
): BatchModificationLockedFilters<TValue> {
	const entries = BATCH_MODIFICATION_LOCKED_FILTER_KEYS.map((key) => [key, mapper(key)] as const);

	return Object.fromEntries(entries) as BatchModificationLockedFilters<TValue>;
}

function getBatchModificationLockedFilterValues(search: Record<string, unknown>) {
	return mapBatchModificationLockedFilters((key) => search[key]);
}

function getBatchModificationLockedFilterLocks(isLocked: boolean) {
	return mapBatchModificationLockedFilters(() => isLocked);
}

export {getBatchModificationLockedFilterLocks, getBatchModificationLockedFilterValues};
