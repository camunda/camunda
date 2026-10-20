/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import i18n from 'i18next';
import {z} from 'zod';
import type {QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';

const VARIABLE_OPERATORS = ['equals', 'notEqual', 'contains', 'oneOf', 'exists', 'doesNotExist'] as const;
type VariableOperator = (typeof VARIABLE_OPERATORS)[number];
type VariableCondition = {name: string; operator: VariableOperator; value: string};
type VariableEntry = NonNullable<NonNullable<QueryProcessInstancesRequestBody['filter']>['variables']>[number];
type ConditionErrors = {name?: string; value?: string};

const STRUCTURAL_CHARS_RE = /[{}[\]"]/;

function isValueRequired(operator: VariableOperator) {
	return operator !== 'exists' && operator !== 'doesNotExist';
}

function parseScalar(input: string): unknown {
	try {
		return JSON.parse(input);
	} catch {
		return input;
	}
}

function smartTransformValue(raw: string, operator: VariableOperator): unknown {
	const isList = operator === 'oneOf';
	const trimmed = (isList ? raw.trim().replace(/^,+|,+$/g, '') : raw).trim();
	if (trimmed === '') {
		return '';
	}
	try {
		return JSON.parse(trimmed);
	} catch {
		if (isList && trimmed.includes(',') && !STRUCTURAL_CHARS_RE.test(trimmed)) {
			return trimmed
				.split(',')
				.map((part) => part.trim())
				.filter((part) => part !== '')
				.map(parseScalar);
		}
		if (!STRUCTURAL_CHARS_RE.test(trimmed)) {
			return trimmed;
		}
		throw new Error(i18n.t('operate.processes.variableFilter.valueInvalid', {value: raw}));
	}
}

function validateCondition({name, operator, value}: VariableCondition): ConditionErrors {
	const errors: ConditionErrors = {};
	if (!name.trim()) {
		errors.name = i18n.t('operate.processes.variableFilter.nameRequired');
	}
	if (isValueRequired(operator)) {
		if (!value.trim()) {
			errors.value = i18n.t('operate.processes.variableFilter.valueRequired');
		} else if (operator !== 'contains') {
			try {
				smartTransformValue(value, operator);
			} catch (error) {
				errors.value = (error as Error).message;
			}
		}
	}
	return errors;
}

function hasErrors(errors: ConditionErrors) {
	return Object.keys(errors).length > 0;
}

const variableConditionSchema = z
	.object({
		name: z.string(),
		operator: z.enum(VARIABLE_OPERATORS),
		value: z.string(),
	})
	.refine((condition) => !hasErrors(validateCondition(condition)));

function toVariableEntry({name, operator, value}: VariableCondition): VariableEntry {
	switch (operator) {
		case 'exists':
		case 'doesNotExist':
			return {name, value: {$exists: operator === 'exists'}};
		case 'contains':
			return {name, value: {$like: `*${value}*`}};
		case 'oneOf': {
			const parsed = smartTransformValue(value, operator);
			return {name, value: {$in: (Array.isArray(parsed) ? parsed : [parsed]).map((item) => JSON.stringify(item))}};
		}
		case 'equals':
			return {name, value: {$eq: JSON.stringify(smartTransformValue(value, operator))}};
		case 'notEqual':
			return {name, value: {$neq: JSON.stringify(smartTransformValue(value, operator))}};
	}
}

export {
	VARIABLE_OPERATORS,
	isValueRequired,
	smartTransformValue,
	validateCondition,
	hasErrors,
	variableConditionSchema,
	toVariableEntry,
};
export type {VariableCondition, VariableOperator, ConditionErrors};
