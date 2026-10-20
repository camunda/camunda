/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {notFound} from '@tanstack/react-router';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {getClientConfig} from '#/shared/config/getClientConfig';

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
};

const SECTION_AVAILABILITY: Partial<Record<AdminSectionKey, (config: AdminSectionConfig) => boolean>> = {
	users: ({isOidc}) => !isOidc,
	'mapping-rules': ({isOidc, isSaas}) => isOidc && !isSaas,
	groups: ({isCamundaGroupsEnabled}) => isCamundaGroupsEnabled,
	tenants: ({isTenantsApiEnabled}) => isTenantsApiEnabled,
};

function getAdminSectionConfig(): AdminSectionConfig {
	const {organizationId, clusterId} = getBootConfig();
	const {authentication, deployment} = getClientConfig();

	return {
		isOidc: authentication.isLoginDelegated,
		isSaas: organizationId !== null && clusterId !== null,
		isTenantsApiEnabled: deployment.isTenantsApiEnabled,
		isCamundaGroupsEnabled: authentication.isCamundaGroupsEnabled,
	};
}

function isAdminSectionAvailable(section: AdminSectionKey, config: AdminSectionConfig): boolean {
	return SECTION_AVAILABILITY[section]?.(config) ?? true;
}

// Mirrors the sidebar order; `roles` is always available, so a landing section always exists.
const LANDING_SECTIONS = ['users', 'mapping-rules', 'groups', 'roles'] as const satisfies AdminSectionKey[];

function getAdminLandingSection(config: AdminSectionConfig): (typeof LANDING_SECTIONS)[number] {
	return LANDING_SECTIONS.find((section) => isAdminSectionAvailable(section, config)) ?? 'roles';
}

function assertAdminSectionAvailable(section: AdminSectionKey): void {
	if (!isAdminSectionAvailable(section, getAdminSectionConfig())) {
		throw notFound({routeId: ADMIN_ROUTE_ID});
	}
}

export {
	type AdminSectionConfig,
	type AdminSectionKey,
	assertAdminSectionAvailable,
	getAdminLandingSection,
	getAdminSectionConfig,
	isAdminSectionAvailable,
};
