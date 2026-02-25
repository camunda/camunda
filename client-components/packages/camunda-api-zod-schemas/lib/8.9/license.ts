/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';

const licenseSchema = z.object({
	validLicense: z.boolean(),
	licenseType: z.string(),
	isCommercial: z.boolean(),
	expiresAt: z.string().nullable(),
});

type License = z.infer<typeof licenseSchema>;

const getLicense: Endpoint = {
	method: 'GET',
	getUrl: () => `/${API_VERSION}/license`,
};

export {licenseSchema, getLicense};
export type {License};
