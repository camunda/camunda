/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const PAGE_SIZES = [10, 20, 50, 100] as const;
const DEFAULT_PAGE_SIZE = 20;

const SORTABLE_FIELDS = ['tenantId', 'name'] as const;

const tenantsSearchSchema = z.object({
	search: z.coerce.string().optional(),
	sortField: z.enum(SORTABLE_FIELDS).optional(),
	sortOrder: z.enum(['ASC', 'DESC']).optional(),
	page: z.number().int().positive().optional(),
	pageSize: z.literal(PAGE_SIZES).optional(),
});

type TenantsSearch = z.infer<typeof tenantsSearchSchema>;

export {DEFAULT_PAGE_SIZE, PAGE_SIZES, tenantsSearchSchema};
export type {TenantsSearch};
