/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {messageCorrelationRequestSchema} from './gen/zod/messageCorrelationRequestSchema';
import {messageCorrelationResultSchema} from './gen/zod/messageCorrelationResultSchema';
import {messagePublicationRequestSchema} from './gen/zod/messagePublicationRequestSchema';
import {messagePublicationResultSchema} from './gen/zod/messagePublicationResultSchema';
import type {MessageCorrelationRequest} from './gen/types/MessageCorrelationRequest';
import type {MessageCorrelationResult} from './gen/types/MessageCorrelationResult';
import type {MessagePublicationRequest} from './gen/types/MessagePublicationRequest';
import type {MessagePublicationResult} from './gen/types/MessagePublicationResult';

const publishMessageRequestBodySchema = messagePublicationRequestSchema;
type PublishMessageRequestBody = MessagePublicationRequest;

const publishMessageResponseBodySchema = messagePublicationResultSchema;
type PublishMessageResponseBody = MessagePublicationResult;

const correlateMessageRequestBodySchema = messageCorrelationRequestSchema;
type CorrelateMessageRequestBody = MessageCorrelationRequest;

const correlateMessageResponseBodySchema = messageCorrelationResultSchema;
type CorrelateMessageResponseBody = MessageCorrelationResult;

const publishMessage = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/messages/publication` as const;
	},
} as const satisfies Endpoint;

const correlateMessage = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/messages/correlation` as const;
	},
} as const satisfies Endpoint;

export {
	publishMessage,
	correlateMessage,
	publishMessageRequestBodySchema,
	publishMessageResponseBodySchema,
	correlateMessageRequestBodySchema,
	correlateMessageResponseBodySchema,
};
export type {
	PublishMessageRequestBody,
	PublishMessageResponseBody,
	CorrelateMessageRequestBody,
	CorrelateMessageResponseBody,
};
