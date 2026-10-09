/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, Form} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {Button, DialogBody, DialogFooter, Input, Label} from '@camunda/design-system';
import type {ClusterVariable} from '@camunda/camunda-api-zod-schemas/8.11';
import {beautifyJSON} from '#/shared/json/beautifyJSON';
import {ClusterVariableDialog} from './ClusterVariableDialog';
import {ClusterVariableValueField} from './ClusterVariableValueField';
import {ClusterVariableValueState} from './ClusterVariableValueState';
import {useClusterVariableMutations} from './useClusterVariableMutations';
import {useClusterVariableValue} from './useClusterVariableValue';
import {validateClusterVariableValue} from './validateClusterVariableValue';

type FormValues = {value: string};

type Props = {
	clusterVariable: ClusterVariable | null;
	onClose: () => void;
};

const EditClusterVariableModal: React.FC<Props> = ({clusterVariable, onClose}) => {
	const {t} = useTranslation();

	if (clusterVariable === null) {
		return null;
	}

	return (
		<ClusterVariableDialog title={t('admin.clusterVariables.editClusterVariableModalTitle')} onClose={onClose}>
			<EditForm clusterVariable={clusterVariable} onClose={onClose} />
		</ClusterVariableDialog>
	);
};

const EditForm: React.FC<{clusterVariable: ClusterVariable; onClose: () => void}> = ({clusterVariable, onClose}) => {
	const {t} = useTranslation();
	const {update} = useClusterVariableMutations();
	const {data, status} = useClusterVariableValue(clusterVariable);

	if (status !== 'success') {
		return (
			<DialogBody>
				<ClusterVariableValueState status={status} />
			</DialogBody>
		);
	}

	return (
		<Form<FormValues>
			initialValues={{value: beautifyJSON(data.value)}}
			onSubmit={({value}) => {
				update.mutate(
					{
						name: clusterVariable.name,
						scope: clusterVariable.scope,
						tenantId: clusterVariable.tenantId,
						value: JSON.parse(value.trim()),
					},
					{onSuccess: onClose},
				);
			}}
			validate={({value}) => ({
				value: validateClusterVariableValue(value, t),
			})}
		>
			{({handleSubmit, form, pristine}) => (
				<>
					<DialogBody>
						<form onSubmit={handleSubmit} className="flex flex-col gap-4">
							<div className="flex flex-col gap-1.5">
								<Label htmlFor="clusterVariableName">{t('admin.clusterVariables.nameFieldLabel')}</Label>
								<Input id="clusterVariableName" value={clusterVariable.name} disabled readOnly />
							</div>
							<Field name="value">
								{({input, meta}) => (
									<ClusterVariableValueField
										id="clusterVariableValue"
										label={t('admin.clusterVariables.valueFieldLabel')}
										value={input.value}
										onChange={(next) => {
											input.onChange(next);
											input.onBlur();
										}}
										errorMessage={meta.touched ? meta.error : undefined}
										autoFocus
									/>
								)}
							</Field>
						</form>
					</DialogBody>
					<DialogFooter>
						<Button type="button" variant="secondary" onClick={onClose}>
							{t('admin.clusterVariables.cancelButton')}
						</Button>
						<Button type="button" loading={update.isPending} disabled={pristine} onClick={form.submit}>
							{t('admin.clusterVariables.saveButton')}
						</Button>
					</DialogFooter>
				</>
			)}
		</Form>
	);
};

export {EditClusterVariableModal};
