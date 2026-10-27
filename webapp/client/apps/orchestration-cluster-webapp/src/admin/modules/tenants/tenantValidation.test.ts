/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {isValidTenantId} from './tenantValidation';

describe('isValidTenantId', () => {
	it.for(['tenant-a', 'tenant_a.1', 'a', 'a'.repeat(31)])('should accept %s', (tenantId) => {
		expect(isValidTenantId(tenantId)).toBe(true);
	});

	it.for(['', 'tenant a', 'tenant/a', 'tenant@a', 'a'.repeat(32)])('should reject %s', (tenantId) => {
		expect(isValidTenantId(tenantId)).toBe(false);
	});
});
