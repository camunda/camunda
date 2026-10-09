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

const clusterVariablesSearchSchema = z.object({
	search: z.coerce.string().optional(),
	sortOrder: z.enum(['ASC', 'DESC']).optional(),
	page: z.number().int().positive().optional(),
	pageSize: z.literal(PAGE_SIZES).optional(),
});

type ClusterVariablesSearch = z.infer<typeof clusterVariablesSearchSchema>;

export {DEFAULT_PAGE_SIZE, PAGE_SIZES, clusterVariablesSearchSchema};
export type {ClusterVariablesSearch};
