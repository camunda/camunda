/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import type {AdminSectionConfig} from '#/admin/adminSections';
import {getAvailableTenantTabs} from './getAvailableTenantTabs';

const config: AdminSectionConfig = {
	isOidc: false,
	isSaas: false,
	isTenantsApiEnabled: true,
	isCamundaGroupsEnabled: true,
};

describe('getAvailableTenantTabs', () => {
	it('should offer users, groups and roles when not using OIDC', () => {
		// when
		const tabs = getAvailableTenantTabs(config);

		// then
		expect(tabs).toEqual(['users', 'groups', 'roles']);
	});

	it('should add mapping rules and clients when using OIDC', () => {
		// when
		const tabs = getAvailableTenantTabs({...config, isOidc: true});

		// then
		expect(tabs).toEqual(['users', 'groups', 'roles', 'mapping-rules', 'clients']);
	});

	it('should omit groups when Camunda groups are disabled', () => {
		// when
		const tabs = getAvailableTenantTabs({...config, isCamundaGroupsEnabled: false});

		// then
		expect(tabs).toEqual(['users', 'roles']);
	});
});
