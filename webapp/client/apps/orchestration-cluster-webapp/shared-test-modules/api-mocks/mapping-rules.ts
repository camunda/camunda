/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {MappingRule, QueryMappingRulesResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';

function createMappingRule(overrides?: Partial<MappingRule>): MappingRule {
	return {
		mappingRuleId: 'my-mapping-rule',
		name: 'My mapping rule',
		claimName: 'email',
		claimValue: 'demo@example.com',
		...overrides,
	};
}

function createQueryMappingRulesResponse(overrides?: {
	items?: MappingRule[];
	page?: Partial<QueryMappingRulesResponseBody['page']>;
}): QueryMappingRulesResponseBody {
	const items = overrides?.items ?? [];
	return {
		items,
		page: {
			totalItems: items.length,
			startCursor: null,
			endCursor: null,
			hasMoreTotalItems: false,
			...overrides?.page,
		},
	};
}

export {createMappingRule, createQueryMappingRulesResponse};
