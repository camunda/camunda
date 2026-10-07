/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryAuthorizationsRequestBody, ResourceType} from '@camunda/camunda-api-zod-schemas/8.11';
import {DEFAULT_PAGE_SIZE, getEffectiveSort, type AuthorizationsSearch} from './searchSchema';

function getAuthorizationsRequestBody(
	search: AuthorizationsSearch,
	resourceType: ResourceType,
): QueryAuthorizationsRequestBody {
	const pageSize = search.pageSize ?? DEFAULT_PAGE_SIZE;
	const ownerId = search.ownerId?.trim();

	return {
		sort: [getEffectiveSort(search, resourceType)],
		filter: ownerId === undefined || ownerId === '' ? {resourceType} : {resourceType, ownerId},
		page: {from: ((search.page ?? 1) - 1) * pageSize, limit: pageSize},
	};
}

export {getAuthorizationsRequestBody};
