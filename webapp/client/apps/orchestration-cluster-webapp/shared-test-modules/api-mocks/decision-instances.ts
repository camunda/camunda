/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	DecisionInstance,
	GetDecisionInstanceResponseBody,
	QueryDecisionInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';

function createDecisionInstance(overrides?: Partial<GetDecisionInstanceResponseBody>): GetDecisionInstanceResponseBody {
	return {
		decisionEvaluationInstanceKey: '123567',
		state: 'EVALUATED',
		evaluationDate: '2022-01-20T13:26:52.000Z',
		evaluationFailure: null,
		decisionDefinitionId: 'invoiceClassification',
		decisionDefinitionName: 'Invoice Classification',
		decisionDefinitionVersion: 1,
		decisionDefinitionType: 'DECISION_TABLE',
		result: '"ok"',
		tenantId: '<default>',
		decisionEvaluationKey: '123567-1',
		processDefinitionKey: '2251799813685251',
		processInstanceKey: '2251799813685252',
		rootProcessInstanceKey: null,
		decisionDefinitionKey: '2251799813685253',
		elementInstanceKey: '2251799813685254',
		rootDecisionDefinitionKey: '2251799813685253',
		businessId: null,
		evaluatedInputs: [],
		matchedRules: [],
		...overrides,
	};
}

function createQueryDecisionInstancesResponse(overrides?: {
	items?: DecisionInstance[];
	page?: Partial<QueryDecisionInstancesResponseBody['page']>;
}): QueryDecisionInstancesResponseBody {
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

export {createDecisionInstance, createQueryDecisionInstancesResponse};
