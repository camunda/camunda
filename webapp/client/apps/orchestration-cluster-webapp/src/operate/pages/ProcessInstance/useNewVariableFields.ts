/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useField} from 'react-final-form';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {useVariableScope} from './useVariableScope';
import {useScopeVariableNames} from './useScopeVariableNames';
import {getLastVariableModification} from './modifications/modificationSelectors';
import {createNewVariableFieldName} from './variableFieldNames';

function useNewVariableFields(fieldPrefix: string) {
	const {state, dispatch} = useProcessInstanceModification();
	const {scopeKey, elementName} = useVariableScope();
	const {isComplete: areVariableNamesComplete} = useScopeVariableNames(scopeKey);
	const {
		meta: {valid: isNameValid, validating: isNameValidating},
		input: {value: currentName},
	} = useField<string>(createNewVariableFieldName(fieldPrefix, 'name'));
	const {
		meta: {valid: isValueValid, validating: isValueValidating},
		input: {value: currentValue},
	} = useField<string>(createNewVariableFieldName(fieldPrefix, 'value'));
	const {
		input: {value: currentId},
	} = useField<string>(createNewVariableFieldName(fieldPrefix, 'id'));
	const isNameFieldValid = !isNameValidating && Boolean(isNameValid);
	const isValueFieldValid = !isValueValidating && Boolean(isValueValid);

	const stageModification = ({areFieldsValid, name, value}: {areFieldsValid: boolean; name: string; value: string}) => {
		if (scopeKey === null || !areVariableNamesComplete || !areFieldsValid || name === '' || value === '') {
			return;
		}

		const lastAddModification = getLastVariableModification(state, scopeKey, currentId, 'ADD_VARIABLE');

		if (
			lastAddModification === undefined ||
			lastAddModification.name !== name ||
			lastAddModification.newValue !== value
		) {
			dispatch({
				type: 'addModification',
				modification: {
					type: 'variable',
					payload: {operation: 'ADD_VARIABLE', scopeId: scopeKey, id: currentId, elementName, name, newValue: value},
				},
			});
		}
	};

	return {
		currentName,
		currentValue,
		isNameFieldValid,
		areFieldsValid: isNameFieldValid && isValueFieldValid,
		stageModification,
	};
}

export {useNewVariableFields};
