/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useForm, useFormState} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {Button} from '@carbon/react';
import {Add} from '@carbon/react/icons';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useCanEditVariables} from './useCanEditVariables';
import {NewVariable} from './NewVariable';
import {CopyVariablesButton} from './CopyVariablesButton';
import type {VariableFormValues} from './variableFormValues';
import {FooterContainer} from './variables.styled';

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
		<FooterContainer data-testid="variables-footer">
			{isAddingVariable ? (
				<NewVariable scopeKey={scopeKey} />
			) : (
				<Button
					kind="ghost"
					size="md"
					renderIcon={Add}
					disabled={isDisabled}
					onClick={() => {
						form.reset({name: '', value: ''});
					}}
				>
					{t('operate.processInstance.variables.addVariable')}
				</Button>
			)}
			<CopyVariablesButton scopeKey={scopeKey} />
		</FooterContainer>
	);
};

export {VariablesFooter};
