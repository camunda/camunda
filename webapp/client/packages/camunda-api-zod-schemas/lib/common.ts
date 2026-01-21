/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const API_VERSION = 'v2';

const queryPageSchema = z
	.object({
		from: z.number().int(),
		limit: z.number().int(),
		before: z.string().optional(),
		after: z.string().optional(),
	})
	.partial();

function getQueryRequestSortSchema<Fields extends [string, ...string[]]>(fields: Fields) {
	return z.array(
		z.object({
			field: z.enum(fields),
			order: z.enum(['asc', 'desc']).optional(),
		}),
	);
}

function getQueryRequestBodySchema<
	FilterSchema extends z.ZodTypeAny,
	SortFields extends [string, ...string[]],
>(options: {sortFields: SortFields; filter: FilterSchema}) {
	const {sortFields, filter} = options;

	return z
		.object({
			sort: getQueryRequestSortSchema(sortFields),
			page: queryPageSchema,
			filter,
		})
		.partial();
}

interface Endpoint<URLParams extends object | undefined = undefined> {
	getUrl: URLParams extends undefined
		? () => string
		: {} extends URLParams
			? (params?: URLParams) => string
			: (params: URLParams) => string;
	method: string;
}

export {API_VERSION, getQueryRequestBodySchema};
export type {Endpoint};
