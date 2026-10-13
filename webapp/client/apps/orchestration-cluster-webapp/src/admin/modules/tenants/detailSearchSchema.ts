/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {PAGE_SIZES} from './searchSchema';

const TENANT_TABS = ['users', 'groups', 'roles', 'mapping-rules', 'clients'] as const;

const tenantDetailSearchSchema = z.object({
	tab: z.enum(TENANT_TABS).optional(),
	sortOrder: z.enum(['ASC', 'DESC']).optional(),
	page: z.number().int().positive().optional(),
	pageSize: z.literal(PAGE_SIZES).optional(),
});

type TenantTab = (typeof TENANT_TABS)[number];
type TenantDetailSearch = z.infer<typeof tenantDetailSearchSchema>;

export {tenantDetailSearchSchema};
export type {TenantDetailSearch, TenantTab};
