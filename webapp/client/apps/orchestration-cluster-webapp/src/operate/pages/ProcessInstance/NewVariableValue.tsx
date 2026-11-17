/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, useForm} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {Layer} from '@carbon/react';
import {InlineJsonEditor} from '#/operate/shared/Editors/InlineJsonEditor';
import {useFieldError} from '#/operate/shared/hooks/useFieldError';
import {mergeValidators} from '#/operate/shared/utils/mergeValidators';
import {useNewVariableFields} from './useNewVariableFields';
import {createNewVariableFieldName} from './variableFieldNames';
import {validateModifiedValueComplete, validateModifiedValueValid} from './variableValidators';

const validateValue = mergeValidators(validateModifiedValueComplete, validateModifiedValueValid);

const NewVariableValue: React.FC<{fieldPrefix: string}> = ({fieldPrefix}) => {
	const {t} = useTranslation();
	const form = useForm();
	const fieldName = createNewVariableFieldName(fieldPrefix, 'value');
	const {currentName, currentValue, areFieldsValid, stageModification} = useNewVariableFields(fieldPrefix);
	const fieldError = useFieldError(fieldName);

	return (
		<Layer>
			<Field name={fieldName} validate={validateValue} parse={(value) => value}>
				{({input, meta}) => (
					<InlineJsonEditor
						value={input.value}
						onChange={input.onChange}
						onFocus={input.onFocus}
						isModified={meta.modified}
						label={t('operate.processInstance.variables.value')}
						id={fieldName}
						fieldError={fieldError}
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

export {NewVariableValue};
