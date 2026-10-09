/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {resourceTypeSchema, type ResourceType} from '@camunda/camunda-api-zod-schemas/8.11';

const PAGE_SIZES = [10, 20, 50, 100] as const;
const DEFAULT_PAGE_SIZE = 20;

const SORTABLE_FIELDS = ['ownerType', 'ownerId', 'resourceId'] as const;
const DEFAULT_SORT_FIELD = 'ownerId';

const authorizationsSearchSchema = z.object({
	resourceType: resourceTypeSchema.optional().catch(undefined),
	ownerId: z.coerce.string().optional(),
	sortField: z.enum(SORTABLE_FIELDS).optional(),
	sortOrder: z.enum(['ASC', 'DESC']).optional(),
	page: z.number().int().positive().optional(),
	pageSize: z.literal(PAGE_SIZES).optional(),
});

type AuthorizationsSearch = z.infer<typeof authorizationsSearchSchema>;

function getAvailableResourceTypes(isTenantsApiEnabled: boolean): ResourceType[] {
	return resourceTypeSchema.options.filter((resourceType) => isTenantsApiEnabled || resourceType !== 'TENANT');
}

function resolveResourceType(requested: ResourceType | undefined, available: ResourceType[]): ResourceType {
	return requested !== undefined && available.includes(requested) ? requested : (available[0] ?? 'AUTHORIZATION');
}

function getEffectiveSort(
	search: Pick<AuthorizationsSearch, 'sortField' | 'sortOrder'>,
	resourceType: ResourceType,
): {field: (typeof SORTABLE_FIELDS)[number]; order: 'ASC' | 'DESC'} {
	// USER_TASK authorizations have no resourceId, so that sort would be meaningless.
	if (search.sortField === undefined || (resourceType === 'USER_TASK' && search.sortField === 'resourceId')) {
		return {field: DEFAULT_SORT_FIELD, order: 'ASC'};
	}

	return {field: search.sortField, order: search.sortOrder ?? 'ASC'};
}

export {
	DEFAULT_PAGE_SIZE,
	DEFAULT_SORT_FIELD,
	PAGE_SIZES,
	authorizationsSearchSchema,
	getAvailableResourceTypes,
	getEffectiveSort,
	resolveResourceType,
};
export type {AuthorizationsSearch};
