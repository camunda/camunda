/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {signalBroadcastRequestSchema, signalBroadcastResultSchema} from './gen';

const broadcastSignalRequestBodySchema = signalBroadcastRequestSchema;
type BroadcastSignalRequestBody = z.infer<typeof broadcastSignalRequestBodySchema>;

const broadcastSignalResponseBodySchema = signalBroadcastResultSchema;
type BroadcastSignalResponseBody = z.infer<typeof broadcastSignalResponseBodySchema>;

const broadcastSignal: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/signals/broadcast`;
	},
};

export {broadcastSignal, broadcastSignalRequestBodySchema, broadcastSignalResponseBodySchema};
export type {BroadcastSignalRequestBody, BroadcastSignalResponseBody};
