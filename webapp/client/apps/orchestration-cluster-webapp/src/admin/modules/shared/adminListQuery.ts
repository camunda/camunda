/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// The search index lags behind writes, so mutations patch the cached lists optimistically. A
// refetch right after a change can replace that data with stale results. Keeping the list fresh
// for a short window avoids that on quick revisits while still refreshing lists afterwards.
const ADMIN_LIST_STALE_TIME_MS = 5000;

/**
 * Applies the shared staleTime to an Admin list query. Use it in both the route loader and the
 * component so the two cannot drift apart.
 */
function adminListQuery<T extends {staleTime?: unknown}>(options: T): T & {staleTime: number} {
	return {...options, staleTime: ADMIN_LIST_STALE_TIME_MS};
}

export {ADMIN_LIST_STALE_TIME_MS, adminListQuery};
