/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getIn} from 'final-form';
import {useField, useFormState} from 'react-final-form';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {useVariableScope} from './useVariableScope';
import {getLastVariableModification} from './modifications/modificationSelectors';
import {createVariableFieldName} from './variableFieldNames';
import type {VariableFormValues} from './variableFormValues';

function useExistingVariableEditor(variableName: string, variableValue: string) {
	const {state, dispatch, isModificationModeEnabled} = useProcessInstanceModification();
	const {scopeKey, elementName} = useVariableScope();
	const {submitting, values} = useFormState<VariableFormValues>();
	const fieldName = isModificationModeEnabled ? createVariableFieldName(variableName) : 'value';
	const {
		meta: {validating, valid},
	} = useField(fieldName);
	const pendingEditModification =
		isModificationModeEnabled && scopeKey !== null
			? getLastVariableModification(state, scopeKey, variableName, 'EDIT_VARIABLE')
			: undefined;

	const getInitialValue = (fullValue?: string) => pendingEditModification?.newValue ?? fullValue ?? variableValue;

	const stageModification = ({
		oldValue,
		newValue,
		isValid = !validating && Boolean(valid),
	}: {
		oldValue: string;
		newValue: string;
		isValid?: boolean;
	}) => {
		if (!isModificationModeEnabled || scopeKey === null || !isValid || newValue === '') {
			return;
		}

		const lastEditModification = getLastVariableModification(state, scopeKey, variableName, 'EDIT_VARIABLE');
		const isDirty = oldValue !== newValue;

		if (
			lastEditModification?.newValue !== newValue &&
			(isDirty || (lastEditModification !== undefined && lastEditModification.newValue !== newValue))
		) {
			dispatch({
				type: 'addModification',
				modification: {
					type: 'variable',
					payload: {
						operation: 'EDIT_VARIABLE',
						id: variableName,
						scopeId: scopeKey,
						elementName,
						name: variableName,
						oldValue: lastEditModification?.oldValue ?? oldValue,
						newValue,
					},
				},
			});
		}
	};

	return {
		fieldName,
		fieldValue: getIn(values, fieldName) as string | undefined,
		isSubmittingForm: submitting,
		getInitialValue,
		stageModification,
	};
}

export {useExistingVariableEditor};
