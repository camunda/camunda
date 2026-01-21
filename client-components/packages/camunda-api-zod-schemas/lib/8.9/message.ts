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
	messagePublicationRequestSchema,
	messagePublicationResultSchema,
	messageCorrelationRequestSchema,
	messageCorrelationResultSchema,
} from './gen';

const publishMessageRequestBodySchema = messagePublicationRequestSchema;
type PublishMessageRequestBody = z.infer<typeof publishMessageRequestBodySchema>;

const publishMessageResponseBodySchema = messagePublicationResultSchema;
type PublishMessageResponseBody = z.infer<typeof publishMessageResponseBodySchema>;

const correlateMessageRequestBodySchema = messageCorrelationRequestSchema;
type CorrelateMessageRequestBody = z.infer<typeof correlateMessageRequestBodySchema>;

const correlateMessageResponseBodySchema = messageCorrelationResultSchema;
type CorrelateMessageResponseBody = z.infer<typeof correlateMessageResponseBodySchema>;

const publishMessage: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/messages/publication`;
	},
};

const correlateMessage: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/messages/correlation`;
	},
};

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
