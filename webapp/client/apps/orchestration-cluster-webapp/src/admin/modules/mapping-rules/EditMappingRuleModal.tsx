/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, Form} from 'react-final-form';
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
import type {MappingRule, UpdateMappingRuleRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {useMappingRuleMutations} from './useMappingRuleMutations';

type Props = {
	mappingRule: MappingRule | null;
	onClose: () => void;
};

const EditMappingRuleModal: React.FC<Props> = ({mappingRule, onClose}) => {
	const {t} = useTranslation();
	const {update} = useMappingRuleMutations();
	const isOpen = mappingRule !== null;

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
				aria-label={t('admin.mappingRules.editMappingRuleModalTitle')}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				{mappingRule !== null ? (
					<Form<UpdateMappingRuleRequestBody>
						initialValues={{
							name: mappingRule.name,
							claimName: mappingRule.claimName,
							claimValue: mappingRule.claimValue,
						}}
						onSubmit={async (values) => {
							await update.mutateAsync({mappingRuleId: mappingRule.mappingRuleId, ...values});
							onClose();
						}}
						validate={({name, claimName, claimValue}) => ({
							name: name ? undefined : t('admin.mappingRules.mappingRuleNameRequiredError'),
							claimName: claimName ? undefined : t('admin.mappingRules.claimNameRequiredError'),
							claimValue: claimValue ? undefined : t('admin.mappingRules.claimValueRequiredError'),
						})}
					>
						{({handleSubmit, form}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('admin.mappingRules.editMappingRuleModalTitle')}</DialogTitle>
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
										<div className="flex flex-col gap-1.5">
											<Label htmlFor="mappingRuleId">{t('admin.mappingRules.mappingRuleIdFieldLabel')}</Label>
											<Input id="mappingRuleId" value={mappingRule.mappingRuleId} disabled readOnly />
										</div>
										<Field name="name">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="mappingRuleName">{t('admin.mappingRules.mappingRuleNameFieldLabel')}</Label>
													<Input
														id="mappingRuleName"
														value={input.value}
														onChange={input.onChange}
														autoFocus
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
									<Button type="button" loading={update.isPending} onClick={form.submit}>
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

export {EditMappingRuleModal};
