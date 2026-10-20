/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import i18n from 'i18next';
import {z} from 'zod';
import {smartTransformValue, type VariableCondition} from './variableConditions';

const jsonConditionSchema = z.strictObject({
	name: z.string().min(1, {error: () => i18n.t('operate.processes.variableFilter.nameRequired')}),
	value: z.union([
		z.string(),
		z.strictObject({$eq: z.string()}),
		z.strictObject({$neq: z.string()}),
		z.strictObject({$like: z.string()}),
		z.strictObject({$in: z.array(z.unknown())}),
		z.strictObject({$notIn: z.array(z.string())}),
		z.strictObject({$exists: z.boolean()}),
	]),
});
const jsonConditionsSchema = z.array(jsonConditionSchema);
const jsonConditionsEditorSchema = z.toJSONSchema(jsonConditionsSchema);

type ParseResult = {ok: true; conditions: VariableCondition[]} | {ok: false; error: string};

function formatConditionError(index: number, error: string) {
	return i18n.t('operate.processes.variableFilter.conditionError', {number: index + 1, error});
}

function toJsonEntry({name, operator, value}: VariableCondition) {
	switch (operator) {
		case 'equals':
			return {name, value};
		case 'notEqual':
			return {name, value: {$neq: value}};
		case 'contains':
			return {name, value: {$like: `*${value}*`}};
		case 'oneOf': {
			try {
				const parsed = smartTransformValue(value, operator);
				return {name, value: {$in: Array.isArray(parsed) ? parsed : [parsed]}};
			} catch {
				// Kept as the raw string so the JSON tab rejects it on Apply instead of matching it literally.
				return {name, value: {$in: value}};
			}
		}
		case 'exists':
		case 'doesNotExist':
			return {name, value: {$exists: operator === 'exists'}};
	}
}

function serializeConditions(conditions: VariableCondition[]) {
	return JSON.stringify(conditions.filter(({name, value}) => name !== '' || value !== '').map(toJsonEntry), null, 2);
}

function fromJsonEntry({name, value}: z.infer<typeof jsonConditionSchema>): VariableCondition | string {
	if (typeof value === 'string') {
		return {name, operator: 'equals', value};
	}
	if ('$eq' in value) {
		return {name, operator: 'equals', value: value.$eq};
	}
	if ('$neq' in value) {
		return {name, operator: 'notEqual', value: value.$neq};
	}
	if ('$like' in value) {
		return {name, operator: 'contains', value: value.$like.replace(/^\*/, '').replace(/\*$/, '')};
	}
	if ('$in' in value) {
		return {name, operator: 'oneOf', value: JSON.stringify(value.$in)};
	}
	if ('$exists' in value) {
		return {name, operator: value.$exists ? 'exists' : 'doesNotExist', value: ''};
	}
	return i18n.t('operate.processes.variableFilter.notInUnsupported');
}

function formatZodError(error: z.ZodError) {
	return error.issues
		.map(({path: [index, ...rest], message}) => {
			const located = rest.length > 0 ? `${message} (at ${rest.map(String).join('.')})` : message;
			return typeof index === 'number' ? formatConditionError(index, located) : located;
		})
		.join('; ');
}

function parseConditionsJson(text: string): ParseResult {
	let raw: unknown;
	try {
		raw = JSON.parse(text.trim() || '[]');
	} catch {
		return {ok: false, error: i18n.t('operate.processes.variableFilter.invalidJson')};
	}

	const result = jsonConditionsSchema.safeParse(raw);
	if (!result.success) {
		return {ok: false, error: formatZodError(result.error)};
	}

	const mapped = result.data.map(fromJsonEntry);
	const errors = mapped.flatMap((entry, index) =>
		typeof entry === 'string' ? [formatConditionError(index, entry)] : [],
	);
	if (errors.length > 0) {
		return {ok: false, error: errors.join('; ')};
	}

	return {ok: true, conditions: mapped.filter((entry) => typeof entry !== 'string')};
}

export {serializeConditions, parseConditionsJson, formatConditionError, jsonConditionsEditorSchema};
