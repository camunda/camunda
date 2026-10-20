/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {Button, Stack, TextInput} from '@carbon/react';
import {Add} from '@carbon/react/icons';
import {Field, useForm, useFormState} from 'react-final-form';
import type {FieldValidator} from 'final-form';
import i18n from 'i18next';
import {promisifyValidator} from '#/operate/shared/utils/promisifyValidator';
import {mergeValidators} from '#/operate/shared/utils/mergeValidators';
import {hasErrors, smartTransformValue, validateCondition, type VariableCondition} from './variableConditions';
import {setVariableConditions, useVariableConditions} from './variableFilterStore';
import {InlineButtonRow} from './styled';

const VALIDATION_TIMEOUT = 750;

type ParentFormValues = {
	variableName?: string;
	variableValues?: string;
};

const validateNameComplete: FieldValidator<string | undefined> = promisifyValidator((name = '', allValues) => {
	const value = (allValues as ParentFormValues | undefined)?.variableValues?.trim() ?? '';
	const trimmedName = name.trim();
	if ((trimmedName === '' && value === '') || trimmedName !== '') {
		return undefined;
	}
	return i18n.t('operate.processes.variableFilter.inlineNameRequired');
}, VALIDATION_TIMEOUT);

const validateValueComplete: FieldValidator<string | undefined> = promisifyValidator((value = '', allValues) => {
	const name = (allValues as ParentFormValues | undefined)?.variableName?.trim() ?? '';
	const trimmedValue = value.trim();
	if ((name === '' && trimmedValue === '') || trimmedValue !== '') {
		return undefined;
	}
	return i18n.t('operate.processes.variableFilter.inlineValueRequired');
}, VALIDATION_TIMEOUT);

const validateValueParseable: FieldValidator<string | undefined> = promisifyValidator((value = '') => {
	if (value.trim() === '') {
		return undefined;
	}
	try {
		smartTransformValue(value, 'equals');
		return undefined;
	} catch (error) {
		return (error as Error).message;
	}
}, VALIDATION_TIMEOUT);

const SingleConditionFields: React.FC = () => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const form = useForm<ParentFormValues>();
	const [seed] = useVariableConditions();
	const lastSyncRef = useRef<{name: string; value: string} | null>(null);
	const {values, errors, validating} = useFormState<ParentFormValues>({
		subscription: {values: true, errors: true, validating: true},
	});
	const rawName = values.variableName ?? '';
	const rawValue = values.variableValues ?? '';
	const nameError: unknown = errors?.variableName;
	const valueError: unknown = errors?.variableValues;

	useEffect(() => {
		const seedName = seed?.name ?? '';
		const seedValue = seed?.value ?? '';
		if (lastSyncRef.current?.name === seedName && lastSyncRef.current.value === seedValue) {
			return;
		}
		lastSyncRef.current = {name: seedName, value: seedValue};
		form.batch(() => {
			form.change('variableName', seedName);
			form.change('variableValues', seedValue);
		});
	}, [seed?.name, seed?.value, form]);

	useEffect(() => {
		const state = form.getState();
		const name = (state.values.variableName ?? '').trim();
		const value = state.values.variableValues ?? '';
		if (state.validating || (lastSyncRef.current?.name === name && lastSyncRef.current.value === value)) {
			return;
		}
		if (!name && value.trim() === '') {
			lastSyncRef.current = {name: '', value: ''};
			setVariableConditions([]);
			return;
		}
		if (state.errors?.variableName === undefined && state.errors?.variableValues === undefined) {
			lastSyncRef.current = {name, value};
			setVariableConditions([{name, operator: 'equals', value}]);
		}
	}, [rawName, rawValue, nameError, valueError, validating, form]);

	const openModal = () => {
		const draft: VariableCondition = {name: rawName.trim(), operator: 'equals', value: rawValue};
		const isDraftEmpty = !draft.name && rawValue.trim() === '';
		const isDraftValid = !hasErrors(validateCondition(draft));
		if (isDraftValid) {
			lastSyncRef.current = {name: draft.name, value: rawValue};
			setVariableConditions([draft]);
		}
		void navigate({
			to: '/operate/processes/filters/variables',
			search: true,
			state: {
				operateVariableFilterOpenedFromList: true,
				...(isDraftEmpty || isDraftValid ? {} : {operateVariableFilterDraft: {name: draft.name, value: rawValue}}),
			},
		});
	};

	return (
		<Stack gap={3}>
			<Field<string | undefined>
				name="variableName"
				validate={validateNameComplete}
				subscription={{value: true, error: true, dirtySinceLastSubmit: true}}
			>
				{({input, meta}) => (
					<TextInput
						id="single-condition-name"
						labelText={t('operate.processes.variableFilter.name')}
						size="sm"
						placeholder={t('operate.processes.variableFilter.namePlaceholder')}
						value={input.value ?? ''}
						onChange={input.onChange}
						onBlur={input.onBlur}
						invalid={!meta.dirtySinceLastSubmit && meta.error !== undefined}
						invalidText={meta.dirtySinceLastSubmit ? undefined : meta.error}
						autoComplete="off"
					/>
				)}
			</Field>
			<Field<string | undefined>
				name="variableValues"
				validate={mergeValidators(validateValueComplete, validateValueParseable)}
				subscription={{value: true, error: true, dirtySinceLastSubmit: true}}
			>
				{({input, meta}) => (
					<TextInput
						id="single-condition-value"
						labelText={t('operate.processes.variableFilter.value')}
						size="sm"
						placeholder={t('operate.processes.variableFilter.valuePlaceholder')}
						value={input.value ?? ''}
						onChange={input.onChange}
						onBlur={input.onBlur}
						invalid={!meta.dirtySinceLastSubmit && meta.error !== undefined}
						invalidText={meta.dirtySinceLastSubmit ? undefined : meta.error}
						autoComplete="off"
					/>
				)}
			</Field>
			<InlineButtonRow>
				<Button kind="ghost" size="sm" renderIcon={Add} onClick={openModal}>
					{t('operate.processes.variableFilter.addCondition')}
				</Button>
			</InlineButtonRow>
		</Stack>
	);
};

export {SingleConditionFields};
