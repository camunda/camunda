/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {licenseResponseSchema} from './gen/zod/licenseResponseSchema';
import type {LicenseResponse} from './gen/types/LicenseResponse';

const licenseSchema = licenseResponseSchema;
type License = LicenseResponse;

const getLicense = {
	method: 'GET',
	getUrl: () => `/${API_VERSION}/license` as const,
} as const satisfies Endpoint;

export {licenseSchema, getLicense};
export type {License};
