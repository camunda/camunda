/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {useForm} from 'react-final-form';
import {useFieldArray} from 'react-final-form-arrays';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {getLastVariableModification} from './modifications/modificationSelectors';
import {createVariableFieldName} from './variableFieldNames';
import type {NewVariableFormValues, VariableFormValues} from './variableFormValues';

function useSyncRemovedVariableModification(scopeKey: string) {
	const form = useForm<VariableFormValues>();
	const {fields} = useFieldArray('newVariables');
	const {state} = useProcessInstanceModification();
	const {lastRemovedModification} = state;
	const handledRemoval = useRef(lastRemovedModification);

	useEffect(() => {
		if (handledRemoval.current === lastRemovedModification) {
			return;
		}
		handledRemoval.current = lastRemovedModification;

		const removed = lastRemovedModification?.modification;
		if (removed === undefined || removed.type === 'token' || removed.payload.scopeId !== scopeKey) {
			return;
		}

		const {operation, id, name, oldValue} = removed.payload;
		const lastModification = getLastVariableModification(state, scopeKey, id, operation);

		if (operation === 'EDIT_VARIABLE') {
			form.change(createVariableFieldName(name), lastModification?.newValue ?? oldValue);
			return;
		}

		const index = ((fields.value ?? []) as Array<NewVariableFormValues | undefined>).findIndex(
			(field) => field?.id === id,
		);
		if (index === -1 || lastRemovedModification?.source === 'variables') {
			return;
		}

		if (lastModification === undefined) {
			fields.remove(index);
		} else {
			form.mutators['update']?.('newVariables', index, {
				id: lastModification.id,
				name: lastModification.name,
				value: lastModification.newValue,
			});
		}
	}, [lastRemovedModification, state, scopeKey, form, fields]);
}

export {useSyncRemovedVariableModification};
