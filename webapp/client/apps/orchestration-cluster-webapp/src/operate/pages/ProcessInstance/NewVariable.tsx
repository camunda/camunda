/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {Field, useForm, useFormState} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {TextInputField} from '#/operate/shared/TextInputField/TextInputField';
import {InlineJsonEditor} from '#/operate/shared/Editors/InlineJsonEditor';
import {RichTextEditorModal} from '#/operate/shared/Editors/RichTextEditorModal';
import {useFieldError} from '#/operate/shared/hooks/useFieldError';
import {mergeValidators} from '#/operate/shared/utils/mergeValidators';
import {useScopeVariableNames} from './useScopeVariableNames';
import {MaximizeButton} from './MaximizeButton';
import {VariableEditButtons} from './VariableEditButtons';
import {
	validateNameCharacters,
	validateNameComplete,
	validateNameNotDuplicate,
	validateValueComplete,
	validateValueValid,
} from './variableValidators';
import type {VariableFormValues} from './variableFormValues';
import {NewVariableLayer, Operations} from './variables.styled';

const NewVariable: React.FC<{scopeKey: string}> = ({scopeKey}) => {
	const {t} = useTranslation();
	const form = useForm<VariableFormValues>();
	const {values, submitting} = useFormState<VariableFormValues>();
	const [isModalVisible, setIsModalVisible] = useState(false);
	const {names: variableNames, isComplete} = useScopeVariableNames(scopeKey);
	const valueError = useFieldError('value');

	useEffect(() => {
		if (isComplete) {
			form.mutators['triggerValidation']?.('name');
		}
	}, [isComplete, form]);

	return (
		<>
			<NewVariableLayer>
				<Field
					name="name"
					validate={mergeValidators(
						validateNameCharacters,
						validateNameComplete(variableNames),
						validateNameNotDuplicate(variableNames),
					)}
					allowNull={false}
					parse={(value) => value}
				>
					{({input}) => (
						<TextInputField
							{...input}
							id="name"
							size="sm"
							hideLabel
							labelText={t('operate.processInstance.variables.name')}
							type="text"
							placeholder={t('operate.processInstance.variables.name')}
							autoFocus
							readOnly={submitting}
						/>
					)}
				</Field>
				<Field
					name="value"
					validate={mergeValidators(validateValueComplete, validateValueValid)}
					parse={(value) => value}
				>
					{({input, meta}) => (
						<InlineJsonEditor
							value={input.value}
							onChange={input.onChange}
							onBlur={input.onBlur}
							onFocus={input.onFocus}
							isModified={meta.modified}
							label={t('operate.processInstance.variables.value')}
							readOnly={submitting}
							id="value"
							fieldError={valueError}
						/>
					)}
				</Field>
				<Operations>
					<MaximizeButton
						onClick={() => {
							if (!submitting) {
								setIsModalVisible(true);
							}
						}}
					/>
					<VariableEditButtons isSaveBlocked={!isComplete} />
				</Operations>
			</NewVariableLayer>
			<RichTextEditorModal
				isVisible={isModalVisible}
				title={
					values.name
						? t('operate.processInstance.variables.editTitle', {name: values.name})
						: t('operate.processInstance.variables.newVariableTitle')
				}
				value={values.value ?? ''}
				onClose={() => setIsModalVisible(false)}
				onApply={(value) => {
					if (form.getState().submitting) {
						return;
					}
					form.change('value', value);
					void form.submit();
					setIsModalVisible(false);
				}}
			/>
		</>
	);
};

export {NewVariable};
