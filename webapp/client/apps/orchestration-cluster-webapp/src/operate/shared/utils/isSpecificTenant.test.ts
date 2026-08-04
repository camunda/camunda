/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {isSpecificTenant} from './isSpecificTenant';

describe('isSpecificTenant', () => {
	it('should be false for undefined', () => {
		expect(isSpecificTenant(undefined)).toBe(false);
	});

	it('should be false for the "all" sentinel', () => {
		expect(isSpecificTenant('all')).toBe(false);
	});

	it('should be true for a specific tenant id', () => {
		expect(isSpecificTenant('<tenant-A>')).toBe(true);
	});
});
