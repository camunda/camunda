/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {Field, useForm} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {Layer} from '@carbon/react';
import {TextInputField} from '#/operate/shared/TextInputField/TextInputField';
import {mergeValidators} from '#/operate/shared/utils/mergeValidators';
import {useNewVariableFields} from './useNewVariableFields';
import {useScopeVariableNames} from './useScopeVariableNames';
import {createNewVariableFieldName} from './variableFieldNames';
import {
	validateModifiedNameComplete,
	validateModifiedNameNotDuplicate,
	validateNameCharacters,
} from './variableValidators';

type Props = {
	fieldPrefix: string;
	scopeKey: string;
};

const NewVariableName: React.FC<Props> = ({fieldPrefix, scopeKey}) => {
	const {t} = useTranslation();
	const form = useForm();
	const {currentName, currentValue, areFieldsValid, stageModification} = useNewVariableFields(fieldPrefix);
	const {names: variableNames, isComplete} = useScopeVariableNames(scopeKey);
	const wasComplete = useRef(isComplete);
	const inputRef = useRef<HTMLInputElement>(null);
	const fieldName = createNewVariableFieldName(fieldPrefix, 'name');

	useEffect(() => {
		if (isComplete && !wasComplete.current) {
			form.mutators['triggerValidation']?.(fieldName);
			const valueFieldName = createNewVariableFieldName(fieldPrefix, 'value');
			stageModification({
				areFieldsValid:
					Boolean(form.getFieldState(fieldName)?.valid) && Boolean(form.getFieldState(valueFieldName)?.valid),
				name: currentName,
				value: currentValue,
			});
		}
		wasComplete.current = isComplete;
	}, [isComplete, form, fieldName, fieldPrefix, stageModification, currentName, currentValue]);

	useEffect(() => {
		inputRef.current?.focus();
	}, []);

	return (
		<Layer>
			<Field
				name={fieldName}
				validate={mergeValidators(
					validateNameCharacters,
					validateModifiedNameComplete,
					validateModifiedNameNotDuplicate(variableNames),
				)}
				allowNull={false}
				parse={(value) => value}
			>
				{({input}) => (
					<TextInputField
						{...input}
						ref={inputRef}
						data-testid="new-variable-name"
						type="text"
						placeholder={t('operate.processInstance.variables.name')}
						id={fieldName}
						size="sm"
						hideLabel
						labelText={t('operate.processInstance.variables.name')}
						onBlur={() => {
							form.mutators['triggerValidation']?.(fieldName);
							input.onBlur();
							stageModification({areFieldsValid, name: currentName, value: currentValue});
						}}
					/>
				)}
			</Field>
		</Layer>
	);
};

export {NewVariableName};
