/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DecisionDefinition, QueryDecisionDefinitionsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';

function createDecisionDefinition(overrides?: Partial<DecisionDefinition>): DecisionDefinition {
	return {
		name: 'My Decision',
		version: 1,
		decisionDefinitionId: 'my-decision',
		decisionRequirementsId: 'my-decision-requirements',
		decisionRequirementsName: 'My Decision Requirements',
		decisionRequirementsVersion: 1,
		tenantId: '<default>',
		decisionDefinitionKey: '2251799813685280',
		decisionRequirementsKey: '2251799813685270',
		...overrides,
	};
}

function createQueryDecisionDefinitionsResponse(overrides?: {
	items?: DecisionDefinition[];
	page?: Partial<QueryDecisionDefinitionsResponseBody['page']>;
}): QueryDecisionDefinitionsResponseBody {
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

export {createDecisionDefinition, createQueryDecisionDefinitionsResponse};
