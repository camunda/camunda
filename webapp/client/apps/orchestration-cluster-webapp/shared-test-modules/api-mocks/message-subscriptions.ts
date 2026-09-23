/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {MessageSubscription, QueryMessageSubscriptionsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';

function createMessageSubscription(overrides?: Partial<MessageSubscription>): MessageSubscription {
	return {
		messageSubscriptionKey: '2251799813685249',
		processDefinitionId: 'order-process',
		processDefinitionKey: '2251799813685248',
		processInstanceKey: null,
		elementId: 'StartEvent_1',
		elementInstanceKey: null,
		messageSubscriptionState: 'CREATED',
		messageSubscriptionType: 'START_EVENT',
		lastUpdatedDate: '2026-09-23T10:00:00.000Z',
		messageName: 'order-received',
		correlationKey: null,
		tenantId: '<default>',
		rootProcessInstanceKey: null,
		toolProperties: {
			'io.camunda.tool:purpose': 'Places a customer order',
			'io.camunda.tool:results': 'An order confirmation number',
			'io.camunda.tool:when_to_use': 'When the customer has confirmed their cart',
			'io.camunda.tool:when_not_to_use': 'When payment has not been authorized yet',
		},
		processDefinitionName: 'Order process',
		processDefinitionVersion: 3,
		toolName: 'place-order',
		inboundConnectorType: null,
		...overrides,
	};
}

function createQueryMessageSubscriptionsResponse(
	overrides?: Partial<QueryMessageSubscriptionsResponseBody>,
): QueryMessageSubscriptionsResponseBody {
	const items = overrides?.items ?? [createMessageSubscription()];

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

export {createMessageSubscription, createQueryMessageSubscriptionsResponse};
