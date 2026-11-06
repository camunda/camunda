/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useForm, useFormState} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {Button} from '@camunda/design-system';
import {Plus} from '@camunda/design-system/icons';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {useCanEditVariables} from '../useCanEditVariables';
import {NewVariable} from './NewVariable';
import {CopyVariablesButton} from './CopyVariablesButton';
import type {VariableFormValues} from '../variableFormValues';

type Props = {
	scopeKey: string;
	isViewMode: boolean;
};

const VariablesFooter: React.FC<Props> = ({scopeKey, isViewMode}) => {
	const {t} = useTranslation();
	const form = useForm<VariableFormValues>();
	const {initialValues} = useFormState<VariableFormValues>();
	const {hasSelection, resolvedElementInstance} = useProcessInstanceElementSelection();
	const canEditVariables = useCanEditVariables();
	const isAddingVariable = canEditVariables && initialValues?.name === '' && initialValues.value === '';
	const isDisabled = !canEditVariables || !isViewMode || (hasSelection && resolvedElementInstance?.state !== 'ACTIVE');

	return (
		<div className="mt-auto flex flex-wrap items-center justify-between border-t border-border p-2">
			{isAddingVariable ? (
				<NewVariable scopeKey={scopeKey} />
			) : (
				<Button
					type="button"
					variant="ghost"
					disabled={isDisabled}
					onClick={() => {
						form.reset({name: '', value: ''});
					}}
				>
					<Plus className="size-4" aria-hidden="true" />
					{t('operate.processInstance.variables.addVariable')}
				</Button>
			)}
			<CopyVariablesButton scopeKey={scopeKey} />
		</div>
	);
};

export {VariablesFooter};
