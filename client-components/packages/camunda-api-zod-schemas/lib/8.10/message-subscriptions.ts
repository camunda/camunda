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
	advancedIntegerFilterSchema,
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
	rootProcessInstanceKey: z.string().nullable(),
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

const correlatedMessageSubscriptionSchema = z.object({
	subscriptionKey: z.string(),
	processDefinitionId: z.string(),
	processDefinitionKey: z.string(),
	processInstanceKey: z.string(),
	elementId: z.string(),
	elementInstanceKey: z.string(),
	correlationTime: z.string(),
	messageName: z.string(),
	correlationKey: z.string(),
	messageKey: z.string(),
	partitionId: z.number().int(),
	tenantId: z.string(),
	rootProcessInstanceKey: z.string().nullable(),
});
type CorrelatedMessageSubscription = z.infer<typeof correlatedMessageSubscriptionSchema>;

const queryCorrelatedMessageSubscriptionRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'correlationKey',
		'correlationTime',
		'elementId',
		'elementInstanceKey',
		'messageKey',
		'messageName',
		'partitionId',
		'processDefinitionId',
		'processDefinitionKey',
		'processInstanceKey',
		'subscriptionKey',
		'tenantId',
	] as const,
	filter: z
		.object({
			correlationKey: advancedStringFilterSchema,
			correlationTime: advancedDateTimeFilterSchema,
			elementId: advancedStringFilterSchema,
			elementInstanceKey: advancedStringFilterSchema,
			messageKey: advancedStringFilterSchema,
			messageName: advancedStringFilterSchema,
			partitionId: advancedIntegerFilterSchema,
			processDefinitionId: advancedStringFilterSchema,
			processDefinitionKey: advancedStringFilterSchema,
			processInstanceKey: advancedStringFilterSchema,
			subscriptionKey: advancedStringFilterSchema,
			tenantId: advancedStringFilterSchema,
		})
		.partial(),
});

type QueryCorrelatedMessageSubscriptionsRequestBody = z.infer<
	typeof queryCorrelatedMessageSubscriptionRequestBodySchema
>;

const queryCorrelatedMessageSubscriptionsResponseBodySchema = getQueryResponseBodySchema(
	correlatedMessageSubscriptionSchema,
);
type QueryCorrelatedMessageSubscriptionsResponseBody = z.infer<
	typeof queryCorrelatedMessageSubscriptionsResponseBodySchema
>;

const queryCorrelatedMessageSubscriptions: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/correlated-message-subscriptions/search`;
	},
};

export {
	queryMessageSubscriptions,
	queryMessageSubscriptionRequestBodySchema,
	queryMessageSubscriptionsResponseBodySchema,
	messageSubscriptionSchema,
	correlatedMessageSubscriptionSchema,
	queryCorrelatedMessageSubscriptionRequestBodySchema,
	queryCorrelatedMessageSubscriptionsResponseBodySchema,
	queryCorrelatedMessageSubscriptions,
};

export type {
	MessageSubscriptionState,
	MessageSubscription,
	QueryMessageSubscriptionsRequestBody,
	QueryMessageSubscriptionsResponseBody,
	CorrelatedMessageSubscription,
	QueryCorrelatedMessageSubscriptionsRequestBody,
	QueryCorrelatedMessageSubscriptionsResponseBody,
};
