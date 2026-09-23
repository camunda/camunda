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

const mcpProcessesSearchSchema = z.object({
	// coerce: a numeric-looking tool name arrives typed as a JS number from the router's
	// search parser, matching the Operations Log and Processes route schemas.
	search: z.coerce.string().optional(),
	sortOrder: z.enum(['asc', 'desc']).optional(),
	page: z.number().int().positive().optional(),
	pageSize: z.literal(PAGE_SIZES).optional(),
});

type McpProcessesSearch = z.infer<typeof mcpProcessesSearchSchema>;

export {DEFAULT_PAGE_SIZE, PAGE_SIZES, mcpProcessesSearchSchema};
export type {McpProcessesSearch};
