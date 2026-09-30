/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, Form, type FieldMetaState} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {X} from '@camunda/design-system/icons';
import {
	Button,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Input,
	Label,
} from '@camunda/design-system';
import type {MappingRule} from '@camunda/camunda-api-zod-schemas/8.10';
import {useMappingRuleMutations} from './useMappingRuleMutations';
import {isDuplicateMappingRuleIdError} from './isDuplicateMappingRuleIdError';

type FormValues = MappingRule;

// The mapping rule ID is the only field that can also fail server-side (duplicate ID), surfaced
// via final-form's `submitError` rather than the synchronous `validate` error.
function getMappingRuleIdError(meta: FieldMetaState<string>): string | undefined {
	if (meta.submitError && !meta.dirtySinceLastSubmit) {
		return meta.submitError;
	}
	return meta.touched ? meta.error : undefined;
}

type Props = {
	isOpen: boolean;
	onClose: () => void;
};

const AddMappingRuleModal: React.FC<Props> = ({isOpen, onClose}) => {
	const {t} = useTranslation();
	const {create} = useMappingRuleMutations();

	return (
		<Dialog
			open={isOpen}
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent
				size="sm"
				showCloseButton={false}
				aria-label={t('admin.mappingRules.addMappingRuleModalTitle')}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				{isOpen ? (
					<Form<FormValues>
						onSubmit={async (values) => {
							try {
								await create.mutateAsync(values);
								onClose();
								return undefined;
							} catch (error) {
								if (isDuplicateMappingRuleIdError(error)) {
									return {mappingRuleId: t('admin.mappingRules.mappingRuleIdAlreadyExistsError')};
								}
								return undefined;
							}
						}}
						validate={({mappingRuleId, name, claimName, claimValue}) => ({
							mappingRuleId: mappingRuleId ? undefined : t('admin.mappingRules.mappingRuleIdRequiredError'),
							name: name ? undefined : t('admin.mappingRules.mappingRuleNameRequiredError'),
							claimName: claimName ? undefined : t('admin.mappingRules.claimNameRequiredError'),
							claimValue: claimValue ? undefined : t('admin.mappingRules.claimValueRequiredError'),
						})}
					>
						{({handleSubmit, form}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('admin.mappingRules.addMappingRuleModalTitle')}</DialogTitle>
									<Button
										type="button"
										variant="ghost"
										size="icon-sm"
										className="absolute top-2 right-2"
										aria-label={t('admin.mappingRules.cancelButton')}
										onClick={onClose}
									>
										<X aria-hidden />
									</Button>
								</DialogHeader>
								<DialogBody>
									<form onSubmit={handleSubmit} className="flex flex-col gap-4">
										<Field name="mappingRuleId">
											{({input, meta}) => {
												const errorMessage = getMappingRuleIdError(meta);
												return (
													<div className="flex flex-col gap-1.5">
														<Label htmlFor="mappingRuleId">{t('admin.mappingRules.mappingRuleIdFieldLabel')}</Label>
														<Input
															id="mappingRuleId"
															placeholder={t('admin.mappingRules.mappingRuleIdFieldPlaceholder')}
															value={input.value}
															onChange={input.onChange}
															autoFocus
															aria-invalid={Boolean(errorMessage)}
															invalidText={errorMessage}
														/>
													</div>
												);
											}}
										</Field>
										<Field name="name">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="mappingRuleName">{t('admin.mappingRules.mappingRuleNameFieldLabel')}</Label>
													<Input
														id="mappingRuleName"
														placeholder={t('admin.mappingRules.mappingRuleNameFieldPlaceholder')}
														value={input.value}
														onChange={input.onChange}
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
													/>
												</div>
											)}
										</Field>
										<Field name="claimName">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="claimName">{t('admin.mappingRules.claimNameFieldLabel')}</Label>
													<Input
														id="claimName"
														placeholder={t('admin.mappingRules.claimNameFieldPlaceholder')}
														value={input.value}
														onChange={input.onChange}
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
													/>
												</div>
											)}
										</Field>
										<Field name="claimValue">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="claimValue">{t('admin.mappingRules.claimValueFieldLabel')}</Label>
													<Input
														id="claimValue"
														placeholder={t('admin.mappingRules.claimValueFieldPlaceholder')}
														value={input.value}
														onChange={input.onChange}
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
													/>
												</div>
											)}
										</Field>
									</form>
								</DialogBody>
								<DialogFooter>
									<Button type="button" variant="secondary" onClick={onClose}>
										{t('admin.mappingRules.cancelButton')}
									</Button>
									<Button type="button" loading={create.isPending} onClick={form.submit}>
										{t('admin.mappingRules.saveButton')}
									</Button>
								</DialogFooter>
							</>
						)}
					</Form>
				) : null}
			</DialogContent>
		</Dialog>
	);
};

export {AddMappingRuleModal};
