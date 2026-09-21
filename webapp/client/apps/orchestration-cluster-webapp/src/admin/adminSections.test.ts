/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {isAdminSectionAvailable, type AdminSectionConfig} from './adminSections';

const SELF_MANAGED: AdminSectionConfig = {
	isOidc: false,
	isSaas: false,
	isMultiTenancyEnabled: false,
};

describe('admin section availability', () => {
	it('should offer users only while Camunda owns them', () => {
		expect(isAdminSectionAvailable('users', SELF_MANAGED)).toBe(true);
		expect(isAdminSectionAvailable('users', {...SELF_MANAGED, isOidc: true})).toBe(false);
	});

	it('should offer mapping rules only where Camunda binds the IdP claims', () => {
		expect(isAdminSectionAvailable('mapping-rules', SELF_MANAGED)).toBe(false);
		expect(isAdminSectionAvailable('mapping-rules', {...SELF_MANAGED, isOidc: true})).toBe(true);
		expect(isAdminSectionAvailable('mapping-rules', {...SELF_MANAGED, isOidc: true, isSaas: true})).toBe(false);
	});

	it('should offer tenants only with multi-tenancy enabled', () => {
		expect(isAdminSectionAvailable('tenants', SELF_MANAGED)).toBe(false);
		expect(isAdminSectionAvailable('tenants', {...SELF_MANAGED, isMultiTenancyEnabled: true})).toBe(true);
	});

	it('should offer the unconditional sections in any configuration', () => {
		const saasWithOidc: AdminSectionConfig = {isOidc: true, isSaas: true, isMultiTenancyEnabled: true};

		expect(isAdminSectionAvailable('roles', SELF_MANAGED)).toBe(true);
		expect(isAdminSectionAvailable('roles', saasWithOidc)).toBe(true);
		expect(isAdminSectionAvailable('operations-log', saasWithOidc)).toBe(true);
	});
});
