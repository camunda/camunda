/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {querySortOrderSchema} from '@camunda/camunda-api-zod-schemas/8.11';

/**
 * Parses a `<field>+<order>` sort search param. The order is unwrapped so a missing order is rejected
 * instead of taking the spec's `ASC` default.
 */
function createSortSearchParamSchema<Field extends z.ZodType<string>>(field: Field) {
	return z
		.preprocess(
			(sort) => (typeof sort === 'string' ? sort.split('+') : sort),
			z.tuple([field, querySortOrderSchema.unwrap()]),
		)
		.transform(([field, order]) => ({field, order}));
}

export {createSortSearchParamSchema};
