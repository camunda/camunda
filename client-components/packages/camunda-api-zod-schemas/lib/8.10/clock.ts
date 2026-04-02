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

const pinClock: Endpoint = {
	method: 'PUT',
	getUrl: () => `/${API_VERSION}/clock`,
};

const resetClock: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/clock/reset`,
};

export {pinClockRequestBodySchema, pinClock, resetClock};

export type {PinClockRequestBody};
