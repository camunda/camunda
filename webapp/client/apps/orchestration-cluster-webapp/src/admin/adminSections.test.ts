/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {
	canManageMappingRuleMembers,
	getAdminLandingSection,
	isAdminSectionAvailable,
	type AdminSectionConfig,
} from './adminSections';

const ALL_CONFIGS: AdminSectionConfig[] = [false, true].flatMap((isOidc) =>
	[false, true].flatMap((isSaas) =>
		[false, true].flatMap((isTenantsApiEnabled) =>
			[false, true].flatMap((isCamundaGroupsEnabled) =>
				[false, true].map((isAdditionalIdpConfigured) => ({
					isOidc,
					isSaas,
					isTenantsApiEnabled,
					isCamundaGroupsEnabled,
					isAdditionalIdpConfigured,
				})),
			),
		),
	),
);

const SELF_MANAGED: AdminSectionConfig = {
	isOidc: false,
	isSaas: false,
	isTenantsApiEnabled: false,
	isCamundaGroupsEnabled: false,
	isAdditionalIdpConfigured: false,
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

	it('should offer mapping rules on SaaS once an additional IdP is configured', () => {
		const saas: AdminSectionConfig = {...SELF_MANAGED, isOidc: true, isSaas: true};

		expect(isAdminSectionAvailable('mapping-rules', {...saas, isAdditionalIdpConfigured: true})).toBe(true);
		expect(isAdminSectionAvailable('mapping-rules', {...SELF_MANAGED, isAdditionalIdpConfigured: true})).toBe(false);
	});

	it('should offer groups only while Camunda manages group membership', () => {
		expect(isAdminSectionAvailable('groups', SELF_MANAGED)).toBe(false);
		expect(isAdminSectionAvailable('groups', {...SELF_MANAGED, isCamundaGroupsEnabled: true})).toBe(true);
	});

	it('should offer tenants only with the tenants API enabled', () => {
		expect(isAdminSectionAvailable('tenants', SELF_MANAGED)).toBe(false);
		expect(isAdminSectionAvailable('tenants', {...SELF_MANAGED, isTenantsApiEnabled: true})).toBe(true);
	});

	it('should offer the unconditional sections in any configuration', () => {
		const saasWithOidc: AdminSectionConfig = {
			isOidc: true,
			isSaas: true,
			isTenantsApiEnabled: true,
			isCamundaGroupsEnabled: true,
			isAdditionalIdpConfigured: false,
		};

		expect(isAdminSectionAvailable('roles', SELF_MANAGED)).toBe(true);
		expect(isAdminSectionAvailable('roles', saasWithOidc)).toBe(true);
		expect(isAdminSectionAvailable('operations-log', saasWithOidc)).toBe(true);
	});
});

describe('mapping rule members', () => {
	it('should be manageable with OIDC outside SaaS', () => {
		expect(canManageMappingRuleMembers({...SELF_MANAGED, isOidc: true})).toBe(true);
	});

	it('should be manageable on SaaS only with an additional IdP', () => {
		const saas: AdminSectionConfig = {...SELF_MANAGED, isOidc: true, isSaas: true};

		expect(canManageMappingRuleMembers(saas)).toBe(false);
		expect(canManageMappingRuleMembers({...saas, isAdditionalIdpConfigured: true})).toBe(true);
	});

	it('should not be manageable without OIDC', () => {
		expect(canManageMappingRuleMembers({...SELF_MANAGED, isAdditionalIdpConfigured: true})).toBe(false);
	});
});

describe('admin landing section', () => {
	it('should keep mapping rules out of the landing on SaaS with an additional IdP', () => {
		const saas: AdminSectionConfig = {
			...SELF_MANAGED,
			isOidc: true,
			isSaas: true,
			isCamundaGroupsEnabled: true,
			isAdditionalIdpConfigured: true,
		};

		expect(getAdminLandingSection(saas)).toBe('groups');
	});

	it('should land on users while Camunda owns them', () => {
		expect(getAdminLandingSection(SELF_MANAGED)).toBe('users');
	});

	it('should land on mapping rules when users are delegated to an IdP', () => {
		expect(getAdminLandingSection({...SELF_MANAGED, isOidc: true})).toBe('mapping-rules');
	});

	it('should land on groups when neither users nor mapping rules are offered', () => {
		expect(getAdminLandingSection({...SELF_MANAGED, isOidc: true, isSaas: true, isCamundaGroupsEnabled: true})).toBe(
			'groups',
		);
	});

	it('should fall back to roles when no earlier section is offered', () => {
		expect(getAdminLandingSection({...SELF_MANAGED, isOidc: true, isSaas: true})).toBe('roles');
	});

	it.for(ALL_CONFIGS.map((config) => [JSON.stringify(config), config] as const))(
		'should always offer roles in %s, as the landing fallback relies on it',
		([, config]) => {
			expect(isAdminSectionAvailable('roles', config)).toBe(true);
		},
	);
});
