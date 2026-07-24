/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import type {QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {parseIds} from './parseIds';

const VALUE_SEPARATOR = '_';

// `AdvancedStringFilter` type derived because it is not exposed by @camunda/camunda-api-zod-schemas.
type AdvancedStringFilter = Extract<NonNullable<QueryProcessInstancesRequestBody['filter']>['businessId'], object>;
type AdvancedStringFilterOperator = keyof AdvancedStringFilter;

const advancedStringFilterSchema = z.object({
	$eq: z.string().optional(),
	$neq: z.string().optional(),
	$exists: z.stringbool().optional(),
	$in: z.array(z.string().min(1)).min(1).optional(),
	$notIn: z.array(z.string().min(1)).min(1).optional(),
	$like: z.string().optional(),
}) satisfies z.ZodType<AdvancedStringFilter>;
const advancedStringFilterKeySchema = z.keyof(advancedStringFilterSchema);

/** Turns an operator value pair into a URL-ready string. */
function encodeFilterOperation(operator: AdvancedStringFilterOperator, rawValue: string) {
	return `${operator.slice(1)}${VALUE_SEPARATOR}${rawValue}`;
}

/** Splits a URL-ready string into an operator value pair and verifies the operator is valid. */
function splitEncodedFilterOperation(encodedOperation: string) {
	const separatorIndex = encodedOperation.indexOf(VALUE_SEPARATOR);
	if (separatorIndex === -1) {
		return null;
	}

	const operator = `$${encodedOperation.slice(0, separatorIndex)}`;
	const value = encodedOperation.slice(separatorIndex + 1);
	const result = advancedStringFilterKeySchema.safeParse(operator);
	if (!result.success) {
		return null;
	}
	return {operator: result.data, value};
}

/**
 * Decodes a URL-ready-encoded advanced string filter value (as produced by
 * {@linkcode encodeFilterOperation}) into the API filter shape, applying the same
 * per-operator transforms as the legacy `advancedStringFilterCodec` decode direction
 * (wildcard-wrapping `$like`, splitting `$in`/`$notIn` into arrays).
 *
 * Legacy builds this same raw, pre-coercion shape but wraps it in a `z.codec`, whose
 * output-schema validation is what actually turns `$exists`'s string into a boolean and
 * rejects a malformed `$in`/`$notIn` (e.g. an empty id from a stray comma). This decoder
 * isn't a codec, so it runs the result through `advancedStringFilterSchema` itself —
 * dropping this step would silently send `$exists` as a string over the wire.
 */
function decodeAdvancedStringFilter(encoded: string): AdvancedStringFilter | undefined {
	const splitOperation = splitEncodedFilterOperation(encoded);
	if (splitOperation === null) {
		return undefined;
	}

	const {operator, value} = splitOperation;
	const raw: z.input<typeof advancedStringFilterSchema> = (() => {
		switch (operator) {
			case '$eq':
			case '$neq':
			case '$exists':
				return {[operator]: value};
			case '$like':
				return {[operator]: `*${value}*`};
			case '$in':
			case '$notIn':
				return {[operator]: parseIds(value)};
			default:
				return {};
		}
	})();

	const result = advancedStringFilterSchema.safeParse(raw);
	return result.success ? result.data : undefined;
}

export {encodeFilterOperation, splitEncodedFilterOperation, decodeAdvancedStringFilter};
export type {AdvancedStringFilterOperator, AdvancedStringFilter};
