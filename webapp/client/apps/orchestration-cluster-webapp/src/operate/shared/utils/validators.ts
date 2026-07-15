/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {FieldValidator} from 'final-form';
import i18n from 'i18next';
import {z} from 'zod';
import {parseIds} from './parseIds';
import {promisifyValidator} from './promisifyValidator';

const VALIDATION_TIMEOUT = 750;

const areIdsTooLong = (value: string) => {
	return parseIds(value).some((id) => id.length > 19);
};

const areIdsComplete = (value: string) => {
	return value === '' || parseIds(value).every((id) => /^[0-9]{16,19}$/.test(id));
};

const validateIdsCharacters: FieldValidator<string | undefined> = (value = '') => {
	if (value !== '' && !/^[0-9]+$/g.test(value.replace(/,/g, '').replace(/\s/g, ''))) {
		return i18n.t('operate.shared.validators.ids');
	}
	return undefined;
};

const validateIdsLength: FieldValidator<string | undefined> = (value = '') => {
	if (areIdsTooLong(value)) {
		return i18n.t('operate.shared.validators.ids');
	}
	return undefined;
};

const validatesIdsComplete: FieldValidator<string | undefined> = promisifyValidator((value = '') => {
	if (!areIdsComplete(value)) {
		return i18n.t('operate.shared.validators.ids');
	}
	return undefined;
}, VALIDATION_TIMEOUT);

const validateParentInstanceIdCharacters: FieldValidator<string | undefined> = (value = '') => {
	if (value !== '' && !/^[0-9]+$/.test(value)) {
		return i18n.t('operate.shared.validators.parentInstanceId');
	}
	return undefined;
};

const validateParentInstanceIdComplete: FieldValidator<string | undefined> = promisifyValidator((value = '') => {
	if (!areIdsComplete(value)) {
		return i18n.t('operate.shared.validators.parentInstanceId');
	}
	return undefined;
}, VALIDATION_TIMEOUT);

const validateParentInstanceIdNotTooLong: FieldValidator<string | undefined> = (value = '') => {
	if (areIdsTooLong(value)) {
		return i18n.t('operate.shared.validators.parentInstanceId');
	}
	return undefined;
};

/**
 * Validates if value contains only characters from a key or UUID
 */
const validateBatchOperationKeyCharacters: FieldValidator<string | undefined> = (value = '') => {
	const schema = z.union([z.string().length(0), z.string().regex(/^[0-9]+$/), z.string().regex(/^[a-f0-9-]{1,36}$/)]);

	if (!schema.safeParse(value).success) {
		return i18n.t('operate.shared.validators.batchOperationKey');
	}
	return undefined;
};

/**
 * Validates if value is a complete key (16-19 characters) or a complete UUID
 */
const validateBatchOperationKeyComplete: FieldValidator<string | undefined> = promisifyValidator((value = '') => {
	const schema = z.union([z.string().length(0), z.string().regex(/^[0-9]{16,19}$/), z.uuid()]);

	if (!schema.safeParse(value).success) {
		return i18n.t('operate.shared.validators.batchOperationKey');
	}
	return undefined;
}, VALIDATION_TIMEOUT);

export {
	validateIdsCharacters,
	validateIdsLength,
	validatesIdsComplete,
	validateParentInstanceIdCharacters,
	validateParentInstanceIdComplete,
	validateParentInstanceIdNotTooLong,
	validateBatchOperationKeyCharacters,
	validateBatchOperationKeyComplete,
	VALIDATION_TIMEOUT,
};
