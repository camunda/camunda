/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {FieldValidator} from 'final-form';
import get from 'lodash/get';
import type {VariablesFormValues} from './types';
import {createVariableFieldName, getNewVariablePrefix} from './variableFieldNames';
import {isValidJSON} from '#/tasklist/modules/json/isValidJSON';
import {promisifyValidator} from './promisifyValidator';

const ERROR_MESSAGES = {
	invalidName: 'Name is invalid',
	emptyName: 'Name has to be filled',
	duplicateName: 'Name must be unique',
	invalidValue: 'Value has to be JSON or a literal',
} as const;

const VALIDATION_DELAY = 1000;

const validateNameCharacters: FieldValidator<string | undefined> = (variableName = '') => {
	if (variableName.includes('"') || /\s+/.test(variableName)) {
		return ERROR_MESSAGES.invalidName;
	}

	return undefined;
};

const validateNameComplete: FieldValidator<string | undefined> = promisifyValidator(
	(variableName = '', allValuesObject, meta) => {
		const allValues = allValuesObject as VariablesFormValues | undefined;
		if (allValues?.newVariables === undefined) {
			return undefined;
		}

		const variableValue =
			(get(allValues, `${getNewVariablePrefix(meta?.name ?? '')}.value`) as string | undefined) ?? '';
		return variableValue.trim() !== '' && variableName.trim() === '' ? ERROR_MESSAGES.emptyName : undefined;
	},
	VALIDATION_DELAY,
);

const validateDuplicateNames: FieldValidator<string | undefined> = promisifyValidator(
	(variableName = '', allValuesObject, meta) => {
		const allValues = allValuesObject as VariablesFormValues | undefined;
		if (allValues?.newVariables === undefined) {
			return undefined;
		}

		if (Object.prototype.hasOwnProperty.call(allValues, createVariableFieldName(variableName))) {
			return ERROR_MESSAGES.duplicateName;
		}

		if (allValues.newVariables.filter((variable) => variable?.name === variableName).length <= 1) {
			return undefined;
		}

		return meta?.active || meta?.error === ERROR_MESSAGES.duplicateName || meta?.validating
			? ERROR_MESSAGES.duplicateName
			: undefined;
	},
	VALIDATION_DELAY,
);

const validateValueComplete: FieldValidator<string | undefined> = promisifyValidator(
	(variableValue = '', allValuesObject, meta) => {
		const allValues = allValuesObject as VariablesFormValues | undefined;
		if (allValues?.newVariables === undefined) {
			return undefined;
		}

		const variableName = (get(allValues, `${getNewVariablePrefix(meta?.name ?? '')}.name`) as string | undefined) ?? '';
		return (variableName === '' && variableValue === '') || isValidJSON(variableValue)
			? undefined
			: ERROR_MESSAGES.invalidValue;
	},
	VALIDATION_DELAY,
);

const validateValueJSON: FieldValidator<string | undefined> = promisifyValidator(
	(value = '') => (isValidJSON(value) ? undefined : ERROR_MESSAGES.invalidValue),
	VALIDATION_DELAY,
);

export {validateDuplicateNames, validateNameCharacters, validateNameComplete, validateValueComplete, validateValueJSON};
