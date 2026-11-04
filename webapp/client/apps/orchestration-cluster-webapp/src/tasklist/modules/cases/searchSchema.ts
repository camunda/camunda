/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const PAGE_SIZES = [25, 50, 100] as const;
const SORT_FIELDS = ['startDate', 'businessId', 'processDefinitionName'] as const;

const casesSearchDefaults = {
	sortField: 'startDate',
	sortOrder: 'desc',
	page: 1,
	pageSize: 25,
} as const;

const casesSearchSchema = z.object({
	search: z.coerce.string().optional(),
	sortField: z.enum(SORT_FIELDS).default(casesSearchDefaults.sortField),
	sortOrder: z.enum(['asc', 'desc']).default(casesSearchDefaults.sortOrder),
	page: z.number().int().positive().default(casesSearchDefaults.page),
	pageSize: z.literal(PAGE_SIZES).default(casesSearchDefaults.pageSize),
});

type CasesSearch = z.infer<typeof casesSearchSchema>;

export {PAGE_SIZES, SORT_FIELDS, casesSearchDefaults, casesSearchSchema};
export type {CasesSearch};
