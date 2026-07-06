/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';

const pinClockRequestBodySchema = z.object({
	timestamp: z.number().int(),
});
type PinClockRequestBody = z.infer<typeof pinClockRequestBodySchema>;

const pinClock = {
	method: 'PUT',
	getUrl: () => `/${API_VERSION}/clock` as const,
} as const satisfies Endpoint;

const resetClock = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/clock/reset` as const,
} as const satisfies Endpoint;

export {pinClockRequestBodySchema, pinClock, resetClock};

export type {PinClockRequestBody};
