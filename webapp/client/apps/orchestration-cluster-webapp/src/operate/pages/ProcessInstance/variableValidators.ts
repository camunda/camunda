/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import i18n from 'i18next';
import {getIn, type FieldValidator} from 'final-form';
import {isValidJSON} from '#/shared/json/isValidJSON';
import {promisifyValidator} from '#/operate/shared/utils/promisifyValidator';
import {getNewVariablePrefix} from './variableFieldNames';
import type {VariableFormValues} from './variableFormValues';

type Validator = FieldValidator<string | undefined>;

const VALIDATION_DELAY = 1000;

const errors = {
	emptyName: () => i18n.t('operate.processInstance.variables.validation.emptyName'),
	invalidName: () => i18n.t('operate.processInstance.variables.validation.invalidName'),
	duplicateName: () => i18n.t('operate.processInstance.variables.validation.duplicateName'),
	invalidValue: () => i18n.t('operate.processInstance.variables.validation.invalidValue'),
	emptyValue: () => i18n.t('operate.processInstance.variables.validation.emptyValue'),
};

const getNewVariableField = (allValues: object | undefined, fieldName: string, suffix: 'name' | 'value'): string =>
	getIn(allValues ?? {}, `${getNewVariablePrefix(fieldName)}.${suffix}`) ?? '';

const validateNameCharacters: Validator = (variableName = '') =>
	variableName.includes('"') || variableName.includes(' ') ? errors.invalidName() : undefined;

const validateModifiedNameComplete: Validator = (variableName = '', allValues, meta) =>
	getNewVariableField(allValues, meta?.name ?? '', 'value').trim() !== '' && variableName === ''
		? errors.emptyName()
		: undefined;

const validateModifiedNameNotDuplicate =
	(variableNames: string[]): Validator =>
	(variableName = '', allValues, meta) => {
		const {newVariables} = (allValues ?? {}) as VariableFormValues;

		if (newVariables === undefined) {
			return undefined;
		}

		if (meta?.dirty && variableNames.includes(variableName)) {
			return errors.duplicateName();
		}

		if (newVariables.filter((variable) => variable?.name === variableName).length <= 1) {
			return undefined;
		}

		return meta?.active || meta?.error === errors.duplicateName() || meta?.validating
			? errors.duplicateName()
			: undefined;
	};

const validateNameComplete =
	(variableNames: string[]): Validator =>
	(variableName = '', allValues, meta) => {
		const {value = ''} = (allValues ?? {}) as VariableFormValues;

		if (value.trim() !== '' && variableName === '') {
			return errors.emptyName();
		}

		return meta?.dirty && variableNames.includes(variableName) ? errors.duplicateName() : undefined;
	};

const validateNameNotDuplicate = (variableNames: string[]): Validator =>
	promisifyValidator(
		(variableName = '', _, meta) =>
			meta?.dirty && variableNames.includes(variableName) ? errors.duplicateName() : undefined,
		VALIDATION_DELAY,
	);

const validateValueComplete: Validator = promisifyValidator((variableValue = '', allValues) => {
	const {name = ''} = (allValues ?? {}) as VariableFormValues;

	return (name === '' && variableValue === '') || variableValue !== '' ? undefined : errors.emptyValue();
}, VALIDATION_DELAY);

const validateValueValid: Validator = promisifyValidator(
	(variableValue = '') => (variableValue === '' || isValidJSON(variableValue) ? undefined : errors.invalidValue()),
	VALIDATION_DELAY,
);

const validateModifiedValueComplete: Validator = (variableValue = '', allValues, meta) => {
	if (!meta?.visited) {
		return undefined;
	}

	const variableName = getNewVariableField(allValues, meta.name, 'name');

	return (variableName === '' && variableValue === '') || variableValue !== '' ? undefined : errors.emptyValue();
};

const validateModifiedValueValid: Validator = (variableValue = '') =>
	variableValue === '' || isValidJSON(variableValue) ? undefined : errors.invalidValue();

const validateValueNotEmpty: Validator = (variableValue = '') =>
	variableValue === '' ? errors.invalidValue() : undefined;

export {
	validateNameCharacters,
	validateNameComplete,
	validateNameNotDuplicate,
	validateValueComplete,
	validateValueValid,
	validateModifiedNameComplete,
	validateModifiedNameNotDuplicate,
	validateModifiedValueComplete,
	validateModifiedValueValid,
	validateValueNotEmpty,
};
