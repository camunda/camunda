/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryMappingRulesRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {DEFAULT_PAGE_SIZE, type MappingRulesSearch} from './searchSchema';

function getMappingRulesRequestBody(search: MappingRulesSearch): QueryMappingRulesRequestBody {
	const pageSize = search.pageSize ?? DEFAULT_PAGE_SIZE;
	const searchTerm = search.search?.trim();

	return {
		sort: [{field: search.sortField ?? 'mappingRuleId', order: search.sortOrder ?? 'ASC'}],
		filter: searchTerm === undefined || searchTerm === '' ? undefined : {mappingRuleId: {$like: `*${searchTerm}*`}},
		page: {from: ((search.page ?? 1) - 1) * pageSize, limit: pageSize},
	};
}

export {getMappingRulesRequestBody};
