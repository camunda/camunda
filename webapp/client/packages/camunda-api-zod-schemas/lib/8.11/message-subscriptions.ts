/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {correlatedMessageSubscriptionResultSchema} from './gen/zod/correlatedMessageSubscriptionResultSchema';
import {correlatedMessageSubscriptionSearchQueryResultSchema} from './gen/zod/correlatedMessageSubscriptionSearchQueryResultSchema';
import {correlatedMessageSubscriptionSearchQuerySchema} from './gen/zod/correlatedMessageSubscriptionSearchQuerySchema';
import {messageSubscriptionResultSchema} from './gen/zod/messageSubscriptionResultSchema';
import {messageSubscriptionSearchQueryResultSchema} from './gen/zod/messageSubscriptionSearchQueryResultSchema';
import {messageSubscriptionSearchQuerySchema} from './gen/zod/messageSubscriptionSearchQuerySchema';
import type {CorrelatedMessageSubscriptionResult} from './gen/types/CorrelatedMessageSubscriptionResult';
import type {CorrelatedMessageSubscriptionSearchQuery} from './gen/types/CorrelatedMessageSubscriptionSearchQuery';
import type {CorrelatedMessageSubscriptionSearchQueryResult} from './gen/types/CorrelatedMessageSubscriptionSearchQueryResult';
import type {MessageSubscriptionResult} from './gen/types/MessageSubscriptionResult';
import type {MessageSubscriptionSearchQuery} from './gen/types/MessageSubscriptionSearchQuery';
import type {MessageSubscriptionSearchQueryResult} from './gen/types/MessageSubscriptionSearchQueryResult';
import type {MessageSubscriptionStateEnumKey} from './gen/types/MessageSubscriptionStateEnum';
import type {MessageSubscriptionTypeEnumKey} from './gen/types/MessageSubscriptionTypeEnum';

type MessageSubscriptionState = MessageSubscriptionStateEnumKey;

type MessageSubscriptionType = MessageSubscriptionTypeEnumKey;

const messageSubscriptionSchema = messageSubscriptionResultSchema;
type MessageSubscription = MessageSubscriptionResult;

const queryMessageSubscriptionRequestBodySchema = messageSubscriptionSearchQuerySchema;
type QueryMessageSubscriptionsRequestBody = MessageSubscriptionSearchQuery;

const queryMessageSubscriptionsResponseBodySchema = messageSubscriptionSearchQueryResultSchema;
type QueryMessageSubscriptionsResponseBody = MessageSubscriptionSearchQueryResult;

const correlatedMessageSubscriptionSchema = correlatedMessageSubscriptionResultSchema;
type CorrelatedMessageSubscription = CorrelatedMessageSubscriptionResult;

const queryCorrelatedMessageSubscriptionRequestBodySchema = correlatedMessageSubscriptionSearchQuerySchema;
type QueryCorrelatedMessageSubscriptionsRequestBody = CorrelatedMessageSubscriptionSearchQuery;

const queryCorrelatedMessageSubscriptionsResponseBodySchema = correlatedMessageSubscriptionSearchQueryResultSchema;
type QueryCorrelatedMessageSubscriptionsResponseBody = CorrelatedMessageSubscriptionSearchQueryResult;

const queryMessageSubscriptions = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/message-subscriptions/search` as const;
	},
} as const satisfies Endpoint;

const queryCorrelatedMessageSubscriptions = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/correlated-message-subscriptions/search` as const;
	},
} as const satisfies Endpoint;

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
	MessageSubscriptionType,
	MessageSubscription,
	QueryMessageSubscriptionsRequestBody,
	QueryMessageSubscriptionsResponseBody,
	CorrelatedMessageSubscription,
	QueryCorrelatedMessageSubscriptionsRequestBody,
	QueryCorrelatedMessageSubscriptionsResponseBody,
};
