/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {License} from '@camunda/camunda-api-zod-schemas/8.10';

function createLicense(overrides?: Partial<License>): License {
	return {
		validLicense: true,
		licenseType: 'production',
		isCommercial: true,
		expiresAt: null,
		...overrides,
	};
}

export {createLicense};
