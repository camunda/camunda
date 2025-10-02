/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
	advancedDateTimeFilterSchema,
	advancedStringFilterSchema,
	API_VERSION,
	type Endpoint,
	getEnumFilterSchema,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
} from './common';

const messageSubscriptionStateSchema = z.enum(['CORRELATED', 'CREATED', 'DELETED', 'MIGRATED']);
type MessageSubscriptionState = z.infer<typeof messageSubscriptionStateSchema>;

const messageSubscriptionSchema = z.object({
	messageSubscriptionKey: z.string(),
	processDefinitionId: z.string(),
	processDefinitionKey: z.string(),
	processInstanceKey: z.string(),
	elementId: z.string(),
	elementInstanceKey: z.string(),
	messageSubscriptionState: messageSubscriptionStateSchema,
	lastUpdatedDate: z.string(),
	messageName: z.string(),
	correlationKey: z.string(),
	tenantId: z.string(),
});
type MessageSubscription = z.infer<typeof messageSubscriptionSchema>;

const queryMessageSubscriptionRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'messageSubscriptionKey',
		'processDefinitionId',
		'processInstanceKey',
		'elementId',
		'elementInstanceKey',
		'messageSubscriptionState',
		'lastUpdatedDate',
		'messageName',
		'correlationKey',
		'tenantId',
	] as const,
	filter: z
		.object({
			messageSubscriptionState: getEnumFilterSchema(messageSubscriptionStateSchema),
			messageSubscriptionKey: advancedStringFilterSchema,
			processDefinitionId: advancedStringFilterSchema,
			lastUpdatedDate: advancedDateTimeFilterSchema,
			processInstanceKey: advancedStringFilterSchema,
			elementId: advancedStringFilterSchema,
			elementInstanceKey: advancedStringFilterSchema,
			messageName: advancedStringFilterSchema,
			correlationKey: advancedStringFilterSchema,
			tenantId: advancedStringFilterSchema,
		})
		.partial(),
});

type QueryMessageSubscriptionsRequestBody = z.infer<typeof queryMessageSubscriptionRequestBodySchema>;

const queryMessageSubscriptionsResponseBodySchema = getQueryResponseBodySchema(messageSubscriptionSchema);
type QueryMessageSubscriptionsResponseBody = z.infer<typeof queryMessageSubscriptionsResponseBodySchema>;

const queryMessageSubscriptions: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/message-subscriptions/search`;
	},
};

export {
	queryMessageSubscriptions,
	queryMessageSubscriptionRequestBodySchema,
	queryMessageSubscriptionsResponseBodySchema,
	messageSubscriptionSchema,
};

export type {
	MessageSubscriptionState,
	MessageSubscription,
	QueryMessageSubscriptionsRequestBody,
	QueryMessageSubscriptionsResponseBody,
};
