/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Form} from 'react-final-form';
import arrayMutators from 'final-form-arrays';
import {FORM_ERROR, type Mutator} from 'final-form';
import {useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Alert} from '@camunda/design-system';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {requestErrorSchema} from '#/shared/http/request';
import {isValidJSON} from '#/shared/json/isValidJSON';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {useProcessInstanceModification} from '../useProcessInstanceModification';
import {getAddVariableModifications} from '../modifications/modificationSelectors';
import {variableQueryKeys} from '../variables.queries';
import {saveVariable} from '../saveVariable';
import {hasScopeVariable} from '../hasScopeVariable';
import {VariablesFormContent} from './VariablesFormContent';
import type {VariableFormValues} from '../variableFormValues';

type Props = {
	scopeKey: string;
	status: 'loading' | 'empty' | 'variables';
};

type SaveError = {
	title: string;
	description?: string;
};

const triggerValidation: Mutator<VariableFormValues> = ([fieldName], state, {changeValue}) => {
	changeValue(state, fieldName, (value) => value);
};

const VariablesForm: React.FC<Props> = ({scopeKey, status}) => {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const {processInstanceId} = useProcessInstancePage();
	const {state, isModificationModeEnabled} = useProcessInstanceModification();
	const [initialValues] = useState<VariableFormValues>(() => {
		const newVariables = isModificationModeEnabled ? getAddVariableModifications(state, scopeKey) : [];
		return newVariables.length === 0 ? {} : {newVariables};
	});

	return (
		<Form<VariableFormValues>
			mutators={{...arrayMutators, triggerValidation}}
			initialValues={initialValues}
			render={({handleSubmit, submitError, dirtySinceLastSubmit}) => {
				const saveError: SaveError | undefined = submitError;

				return (
					<>
						<VariablesFormContent onSubmit={handleSubmit} scopeKey={scopeKey} status={status} />
						{saveError !== undefined && !dirtySinceLastSubmit && (
							<Alert
								variant="destructive"
								title={saveError.title}
								description={saveError.description}
								className="shrink-0"
							/>
						)}
					</>
				);
			}}
			onSubmit={async ({name, value}, form) => {
				const {initialValues: submittedInitialValues} = form.getState();

				if (name === undefined || value === undefined || !isValidJSON(value)) {
					return undefined;
				}

				try {
					if (
						submittedInitialValues?.name === '' &&
						(await hasScopeVariable(queryClient, {processInstanceKey: processInstanceId, scopeKey, name}))
					) {
						return {name: t('operate.processInstance.variables.validation.duplicateName')};
					}

					await saveVariable(queryClient, {
						processInstanceKey: processInstanceId,
						scopeKey,
						name,
						value: JSON.stringify(JSON.parse(value)),
						variableKey: submittedInitialValues?.variableKey,
					});
					notificationsStore.displayNotification({
						kind: 'success',
						title: t(
							submittedInitialValues?.name === ''
								? 'operate.processInstance.variables.added'
								: 'operate.processInstance.variables.updated',
						),
						isDismissable: true,
					});
				} catch (error) {
					const response = requestErrorSchema.safeParse(error).data?.response ?? null;
					await queryClient.invalidateQueries({queryKey: variableQueryKeys.instanceVariables(processInstanceId)});
					return {
						[FORM_ERROR]:
							response?.status === 403
								? {
										title: t('operate.shared.operations.forbiddenTitle'),
										description: t('operate.shared.operations.forbiddenSubtitle'),
									}
								: {
										title: t('operate.processInstance.variables.saveFailed'),
										description: response?.statusText,
									},
					};
				}

				form.reset({});
				await queryClient.invalidateQueries({queryKey: variableQueryKeys.instanceVariables(processInstanceId)});
				return undefined;
			}}
		/>
	);
};

export {VariablesForm};
