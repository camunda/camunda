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
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {useProcessInstanceModification} from '../useProcessInstanceModification';
import {useVariableScope} from '../useVariableScope';
import {useSyncRemovedVariableModification} from '../useSyncRemovedVariableModification';
import {getAddVariableModifications} from '../modifications/modificationSelectors';
import {generateId} from '../modifications/modificationOperations';
import {VariablesTable} from './VariablesTable';
import {VariablesFooter} from './VariablesFooter';
import type {VariableFormValues} from '../variableFormValues';

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
		<form onSubmit={onSubmit} className="flex h-full min-h-0 flex-col">
			{isVariableModificationAllowed && (
				<Button
					type="button"
					variant="ghost"
					className="self-end"
					disabled={submitting || hasValidationErrors || validating || hasEmptyNewVariable}
					onClick={() => {
						form.mutators['push']?.('newVariables', {id: generateId()});
					}}
				>
					<Plus className="size-4" aria-hidden="true" />
					{t('operate.processInstance.variables.addVariable')}
				</Button>
			)}
			<div className="flex min-h-0 flex-1 flex-col justify-between">
				{isViewMode && status === 'empty' && (
					<div className="flex flex-1 items-center justify-center">
						<EmptyMessage message={t('operate.processInstance.variables.empty')} />
					</div>
				)}
				{(!isViewMode || status === 'variables') && <VariablesTable scopeKey={scopeKey} />}
				{!isModificationModeEnabled && <VariablesFooter scopeKey={scopeKey} isViewMode={isViewMode} />}
			</div>
		</form>
	);
};

export {VariablesFormContent};
