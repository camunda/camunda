/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useForm, useFormState} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {Add} from '@carbon/react/icons';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {useVariableScope} from './useVariableScope';
import {useSyncRemovedVariableModification} from './useSyncRemovedVariableModification';
import {getAddVariableModifications} from './modifications/modificationSelectors';
import {generateId} from './modifications/modificationOperations';
import {VariablesTable} from './VariablesTable';
import {VariablesFooter} from './VariablesFooter';
import type {VariableFormValues} from './variableFormValues';
import {
	EmptyMessageContainer,
	Form,
	ModificationAddVariableButton,
	VariablesContainer,
	VariablesContent,
} from './variables.styled';

type Props = {
	onSubmit: React.FormEventHandler<HTMLFormElement>;
	scopeKey: string;
	status: 'loading' | 'empty' | 'variables';
};

const VariablesFormContent: React.FC<Props> = ({onSubmit, scopeKey, status}) => {
	const {t} = useTranslation();
	const form = useForm<VariableFormValues>();
	const {values, initialValues, submitting, hasValidationErrors, validating} = useFormState<VariableFormValues>();
	const {state, isModificationModeEnabled} = useProcessInstanceModification();
	const {isVariableModificationAllowed} = useVariableScope();
	useSyncRemovedVariableModification(scopeKey);
	const hasEmptyNewVariable = values.newVariables?.some(
		(variable) => variable === undefined || variable.name === undefined || variable.value === undefined,
	);
	const isViewMode = isModificationModeEnabled
		? (values.newVariables ?? []).length === 0 && getAddVariableModifications(state, scopeKey).length === 0
		: initialValues === undefined || Object.values(initialValues).length === 0;

	return (
		<Form onSubmit={onSubmit}>
			{isVariableModificationAllowed && (
				<ModificationAddVariableButton
					kind="ghost"
					size="md"
					renderIcon={Add}
					disabled={submitting || hasValidationErrors || validating || hasEmptyNewVariable}
					onClick={() => {
						form.mutators['push']?.('newVariables', {id: generateId()});
					}}
				>
					{t('operate.processInstance.variables.addVariable')}
				</ModificationAddVariableButton>
			)}
			<VariablesContainer>
				<VariablesContent>
					{isViewMode && status === 'empty' && (
						<EmptyMessageContainer>
							<EmptyMessage message={t('operate.processInstance.variables.empty')} />
						</EmptyMessageContainer>
					)}
					{(!isViewMode || status === 'variables') && <VariablesTable scopeKey={scopeKey} />}
					{!isModificationModeEnabled && <VariablesFooter scopeKey={scopeKey} isViewMode={isViewMode} />}
				</VariablesContent>
			</VariablesContainer>
		</Form>
	);
};

export {VariablesFormContent};
