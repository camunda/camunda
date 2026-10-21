/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AdminSectionConfig} from '#/admin/adminSections';
import type {TenantTab} from './detailSearchSchema';

function getAvailableTenantTabs({isOidc}: AdminSectionConfig): TenantTab[] {
	return ['users', 'groups', 'roles', ...(isOidc ? (['mapping-rules', 'clients'] as const) : [])];
}

function getActiveTenantTab(tabs: TenantTab[], requested: TenantTab | undefined): TenantTab {
	return requested !== undefined && tabs.includes(requested) ? requested : (tabs[0] ?? 'users');
}

export {getAvailableTenantTabs, getActiveTenantTab};
