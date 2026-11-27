/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {notFound} from '@tanstack/react-router';
import {useQuery, type QueryClient} from '@tanstack/react-query';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {queries} from '#/shared/http/queries';

const ADMIN_ROUTE_ID = '/_shadcn/_auth/admin';

type AdminSectionKey =
	| 'users'
	| 'mapping-rules'
	| 'groups'
	| 'roles'
	| 'tenants'
	| 'authorizations'
	| 'global-task-listeners'
	| 'cluster-variables'
	| 'mcp-processes'
	| 'operations-log';

type AdminSectionConfig = {
	isOidc: boolean;
	isSaas: boolean;
	isTenantsApiEnabled: boolean;
	isCamundaGroupsEnabled: boolean;
	isAdditionalIdpConfigured: boolean;
};

const SECTION_AVAILABILITY: Partial<Record<AdminSectionKey, (config: AdminSectionConfig) => boolean>> = {
	users: ({isOidc}) => !isOidc,
	'mapping-rules': canManageMappingRuleMembers,
	groups: ({isCamundaGroupsEnabled}) => isCamundaGroupsEnabled,
	tenants: ({isTenantsApiEnabled}) => isTenantsApiEnabled,
};

function getAdminSectionConfig(isAdditionalIdpConfigured = false): AdminSectionConfig {
	const {organizationId, clusterId} = getBootConfig();
	const {authentication, deployment} = getClientConfig();

	return {
		isOidc: authentication.isLoginDelegated,
		isSaas: organizationId !== null && clusterId !== null,
		isTenantsApiEnabled: deployment.isTenantsApiEnabled,
		isCamundaGroupsEnabled: authentication.isCamundaGroupsEnabled,
		isAdditionalIdpConfigured,
	};
}

async function loadAdminSectionConfig(queryClient: QueryClient): Promise<AdminSectionConfig> {
	const {isOidc, isSaas} = getAdminSectionConfig();
	if (!isOidc || !isSaas) {
		return getAdminSectionConfig();
	}

	const {isAdditionalIdpConfigured} = await queryClient.query(queries.adminClientConfig());
	return getAdminSectionConfig(isAdditionalIdpConfigured);
}

function useAdminSectionConfig(enabled: boolean): AdminSectionConfig {
	const {isOidc, isSaas} = getAdminSectionConfig();
	const {data} = useQuery({...queries.adminClientConfig(), enabled: enabled && isOidc && isSaas});

	return getAdminSectionConfig(data?.isAdditionalIdpConfigured);
}

/**
 * Whether mapping rules can be assigned to and unassigned from roles and groups. Mirrors the
 * server's MappingRuleMembershipPolicy: with OIDC, outside SaaS, and on SaaS only once an
 * additional IdP is configured.
 */
function canManageMappingRuleMembers({isOidc, isSaas, isAdditionalIdpConfigured}: AdminSectionConfig): boolean {
	return isOidc && (!isSaas || isAdditionalIdpConfigured);
}

function isAdminSectionAvailable(section: AdminSectionKey, config: AdminSectionConfig): boolean {
	return SECTION_AVAILABILITY[section]?.(config) ?? true;
}

// Mirrors the sidebar order; `roles` is always available, so a landing section always exists.
const LANDING_SECTIONS = ['users', 'mapping-rules', 'groups', 'roles'] as const satisfies AdminSectionKey[];

function ignoreAdditionalIdp(config: AdminSectionConfig): AdminSectionConfig {
	return {...config, isAdditionalIdpConfigured: false};
}

function getAdminLandingSection(config: AdminSectionConfig): (typeof LANDING_SECTIONS)[number] {
	const landingConfig = ignoreAdditionalIdp(config);
	return LANDING_SECTIONS.find((section) => isAdminSectionAvailable(section, landingConfig)) ?? 'roles';
}

function assertAdminSectionAvailable(section: AdminSectionKey, config = getAdminSectionConfig()): void {
	if (!isAdminSectionAvailable(section, config)) {
		throw notFound({routeId: ADMIN_ROUTE_ID});
	}
}

export {
	type AdminSectionConfig,
	type AdminSectionKey,
	assertAdminSectionAvailable,
	canManageMappingRuleMembers,
	getAdminLandingSection,
	getAdminSectionConfig,
	isAdminSectionAvailable,
	loadAdminSectionConfig,
	useAdminSectionConfig,
};
