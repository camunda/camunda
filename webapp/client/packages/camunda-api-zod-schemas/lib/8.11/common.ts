/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {problemDetailSchema} from './gen/zod/problemDetailSchema';
import {searchQueryPageRequestSchema} from './gen/zod/searchQueryPageRequestSchema';
import {sortOrderEnumSchema} from './gen/zod/sortOrderEnumSchema';
import type {ProblemDetail} from './gen/types/ProblemDetail';
import type {SearchQueryPageRequest} from './gen/types/SearchQueryPageRequest';
import type {SortOrderEnumKey} from './gen/types/SortOrderEnum';

const API_VERSION = 'v2';

const problemDetailsSchema = problemDetailSchema;
type ProblemDetails = ProblemDetail;

const querySortOrderSchema = sortOrderEnumSchema;
type QuerySortOrder = SortOrderEnumKey;

const queryPageSchema = searchQueryPageRequestSchema;
type QueryPage = SearchQueryPageRequest;

// Only used by the activatable activities schemas, which have no equivalent in the REST API spec.
function getCollectionResponseBodySchema<ItemSchema extends z.ZodTypeAny>(
	itemSchema: ItemSchema,
): z.ZodType<{items: z.infer<ItemSchema>[]}> {
	return z.object({
		items: z.array(itemSchema),
	});
}

interface Endpoint<URLParams extends object | undefined = undefined> {
	getUrl: URLParams extends undefined
		? () => string
		: {} extends URLParams
			? (params?: URLParams) => string
			: (params: URLParams) => string;
	method: string;
}

const problemDetailResponseSchema = problemDetailSchema;
type ProblemDetailsResponse = ProblemDetail;

export {
	API_VERSION,
	problemDetailsSchema,
	querySortOrderSchema,
	queryPageSchema,
	getCollectionResponseBodySchema,
	problemDetailResponseSchema,
};
export type {ProblemDetails, QuerySortOrder, QueryPage, Endpoint, ProblemDetailsResponse};
