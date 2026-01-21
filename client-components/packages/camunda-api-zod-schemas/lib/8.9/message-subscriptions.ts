/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {
	messageSubscriptionResultSchema,
	messageSubscriptionSearchQuerySchema,
	messageSubscriptionSearchQueryResultSchema,
	correlatedMessageSubscriptionResultSchema,
	correlatedMessageSubscriptionSearchQuerySchema,
	correlatedMessageSubscriptionSearchQueryResultSchema,
	messageSubscriptionStateEnumSchema,
} from './gen';

const messageSubscriptionStateSchema = messageSubscriptionStateEnumSchema;
type MessageSubscriptionState = z.infer<typeof messageSubscriptionStateSchema>;

const messageSubscriptionSchema = messageSubscriptionResultSchema;
type MessageSubscription = z.infer<typeof messageSubscriptionSchema>;

const queryMessageSubscriptionRequestBodySchema = messageSubscriptionSearchQuerySchema;
type QueryMessageSubscriptionsRequestBody = z.infer<typeof queryMessageSubscriptionRequestBodySchema>;

const queryMessageSubscriptionsResponseBodySchema = messageSubscriptionSearchQueryResultSchema;
type QueryMessageSubscriptionsResponseBody = z.infer<typeof queryMessageSubscriptionsResponseBodySchema>;

const queryMessageSubscriptions: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/message-subscriptions/search`;
	},
};

const correlatedMessageSubscriptionSchema = correlatedMessageSubscriptionResultSchema;
type CorrelatedMessageSubscription = z.infer<typeof correlatedMessageSubscriptionSchema>;

const queryCorrelatedMessageSubscriptionRequestBodySchema = correlatedMessageSubscriptionSearchQuerySchema;
type QueryCorrelatedMessageSubscriptionsRequestBody = z.infer<
	typeof queryCorrelatedMessageSubscriptionRequestBodySchema
>;

const queryCorrelatedMessageSubscriptionsResponseBodySchema = correlatedMessageSubscriptionSearchQueryResultSchema;
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
	messageSubscriptionStateSchema,
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
