/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryAuditLogsRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {DEFAULT_PAGE_SIZE, type OperationsLogSearch} from './searchSchema';

const DEFAULT_SORT: QueryAuditLogsRequestBody['sort'] = [{field: 'timestamp', order: 'DESC'}];

// This page only ever shows admin-category audit logs — hardcoded, not user-facing.
const ADMIN_CATEGORY_FILTER = {$eq: 'ADMIN'} as const;

function getAuditLogsRequestBody(search: OperationsLogSearch): QueryAuditLogsRequestBody {
	const pageSize = search.pageSize ?? DEFAULT_PAGE_SIZE;
	const isAuthorizationEntity = search.entityType === 'AUTHORIZATION';

	return {
		sort: search.sortField === undefined ? DEFAULT_SORT : [{field: search.sortField, order: search.sortOrder ?? 'ASC'}],
		filter: {
			category: ADMIN_CATEGORY_FILTER,
			operationType: search.operationType,
			entityType: search.entityType,
			// The related-entity fields only apply to AUTHORIZATION entries; the UI clears them
			// when entityType changes away from AUTHORIZATION, but the builder guards against
			// stale search state reaching the request regardless.
			relatedEntityType: isAuthorizationEntity ? search.relatedEntityType : undefined,
			relatedEntityKey: isAuthorizationEntity ? search.relatedEntityKey : undefined,
			result: search.result,
			actorId: search.actor,
			timestamp:
				search.timestampFrom && search.timestampTo ? {$gte: search.timestampFrom, $lte: search.timestampTo} : undefined,
		},
		page: {
			from: ((search.page ?? 1) - 1) * pageSize,
			limit: pageSize,
		},
	};
}

/** Turns an API enum value (e.g. `MAPPING_RULE`) into a display label (`Mapping rule`). */
function formatEnumLabel(value: string): string {
	const spaced = value.replace(/_/g, ' ');
	return spaced.charAt(0).toUpperCase() + spaced.slice(1).toLowerCase();
}

export {getAuditLogsRequestBody, formatEnumLabel};
