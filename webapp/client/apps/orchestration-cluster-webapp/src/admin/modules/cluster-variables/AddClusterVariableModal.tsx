/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, Form, type FieldMetaState} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {Button, DialogBody, DialogFooter, Input, Label, RadioGroup, RadioGroupItem} from '@camunda/design-system';
import type {ClusterVariableScope} from '@camunda/camunda-api-zod-schemas/8.11';
import {ClusterVariableDialog} from './ClusterVariableDialog';
import {ClusterVariableTenantSelect} from './ClusterVariableTenantSelect';
import {ClusterVariableValueField} from './ClusterVariableValueField';
import {isDuplicateClusterVariableError} from './isDuplicateClusterVariableError';
import {useClusterVariableMutations} from './useClusterVariableMutations';
import {validateClusterVariableValue} from './validateClusterVariableValue';

const VALID_NAME_PATTERN = /^[a-zA-Z0-9_~@.+-]{1,256}$/;

type FormValues = {
	name: string;
	scope: ClusterVariableScope;
	tenantId?: string;
	value: string;
};

// The name is the only field that can also fail server-side (duplicate name), surfaced via
// final-form's `submitError` rather than the synchronous `validate` error.
function getNameError(meta: FieldMetaState<string>): string | undefined {
	if (meta.submitError && !meta.dirtySinceLastSubmit) {
		return meta.submitError;
	}
	return meta.touched ? meta.error : undefined;
}

type Props = {
	isOpen: boolean;
	isTenantScopeAvailable: boolean;
	onClose: () => void;
};

const AddClusterVariableModal: React.FC<Props> = ({isOpen, isTenantScopeAvailable, onClose}) => {
	const {t} = useTranslation();
	const {create} = useClusterVariableMutations();

	if (!isOpen) {
		return null;
	}

	return (
		<ClusterVariableDialog title={t('admin.clusterVariables.addClusterVariableModalTitle')} onClose={onClose}>
			<Form<FormValues>
				initialValues={{name: '', scope: 'GLOBAL', value: ''}}
				onSubmit={async ({name, scope, tenantId, value}) => {
					try {
						await create.mutateAsync({
							name: name.trim(),
							scope,
							tenantId: scope === 'TENANT' ? (tenantId ?? null) : null,
							value: JSON.parse(value.trim()),
						});
						onClose();
						return undefined;
					} catch (error) {
						if (await isDuplicateClusterVariableError(error)) {
							return {name: t('admin.clusterVariables.nameAlreadyExistsError')};
						}
						return undefined;
					}
				}}
				validate={({name, scope, tenantId, value}) => ({
					name: !name?.trim()
						? t('admin.clusterVariables.nameRequiredError')
						: VALID_NAME_PATTERN.test(name.trim())
							? undefined
							: t('admin.clusterVariables.nameInvalidError'),
					tenantId: scope === 'TENANT' && !tenantId ? t('admin.clusterVariables.tenantRequiredError') : undefined,
					value: validateClusterVariableValue(value, t),
				})}
			>
				{({handleSubmit, form, values}) => (
					<>
						<DialogBody>
							<form onSubmit={handleSubmit} className="flex flex-col gap-4">
								<Field name="name">
									{({input, meta}) => {
										const errorMessage = getNameError(meta);
										return (
											<div className="flex flex-col gap-1.5">
												<Label htmlFor="clusterVariableName">{t('admin.clusterVariables.nameFieldLabel')}</Label>
												<Input
													id="clusterVariableName"
													placeholder={t('admin.clusterVariables.nameFieldPlaceholder')}
													value={input.value}
													onChange={input.onChange}
													autoComplete="off"
													autoFocus
													aria-invalid={Boolean(errorMessage)}
													invalidText={errorMessage}
												/>
											</div>
										);
									}}
								</Field>
								<Field name="scope">
									{({input}) => (
										<div className="flex flex-col gap-1.5">
											<Label id="clusterVariableScopeLabel">{t('admin.clusterVariables.scopeFieldLabel')}</Label>
											<RadioGroup
												aria-labelledby="clusterVariableScopeLabel"
												value={input.value}
												onValueChange={(scope) => input.onChange(scope as ClusterVariableScope)}
												className="flex flex-row gap-4"
											>
												<Label className="flex items-center gap-2">
													<RadioGroupItem value="GLOBAL" />
													{t('admin.clusterVariables.scopeGlobal')}
												</Label>
												<Label className="flex items-center gap-2">
													<RadioGroupItem value="TENANT" disabled={!isTenantScopeAvailable} />
													{t('admin.clusterVariables.scopeTenant')}
												</Label>
											</RadioGroup>
										</div>
									)}
								</Field>
								{values.scope === 'TENANT' ? (
									<Field name="tenantId">
										{({input, meta}) => (
											<ClusterVariableTenantSelect
												value={input.value || undefined}
												onChange={input.onChange}
												errorMessage={meta.touched ? meta.error : undefined}
											/>
										)}
									</Field>
								) : null}
								<Field name="value">
									{({input, meta}) => (
										<ClusterVariableValueField
											id="clusterVariableValue"
											label={t('admin.clusterVariables.valueCreateFieldLabel')}
											value={input.value}
											onChange={(next) => {
												input.onChange(next);
												input.onBlur();
											}}
											errorMessage={meta.touched ? meta.error : undefined}
										/>
									)}
								</Field>
							</form>
						</DialogBody>
						<DialogFooter>
							<Button type="button" variant="secondary" onClick={onClose}>
								{t('admin.clusterVariables.cancelButton')}
							</Button>
							<Button type="button" loading={create.isPending} onClick={form.submit}>
								{t('admin.clusterVariables.createButton')}
							</Button>
						</DialogFooter>
					</>
				)}
			</Form>
		</ClusterVariableDialog>
	);
};

export {AddClusterVariableModal};
