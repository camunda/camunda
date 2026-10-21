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
	Textarea,
} from '@camunda/design-system';
import type {Tenant, UpdateTenantRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {useTenantMutations} from './useTenantMutations';
import {MAX_DESCRIPTION_LENGTH} from './tenantValidation';

type Props = {
	tenant: Tenant | null;
	onClose: () => void;
};

const EditTenantModal: React.FC<Props> = ({tenant, onClose}) => {
	const {t} = useTranslation();
	const {update} = useTenantMutations();
	const isOpen = tenant !== null;

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
				aria-label={t('admin.tenants.editTenantModalTitle')}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				{tenant !== null ? (
					<Form<UpdateTenantRequestBody>
						initialValues={{name: tenant.name, description: tenant.description ?? ''}}
						onSubmit={(values) => {
							update.mutate({tenantId: tenant.tenantId, ...values}, {onSuccess: onClose});
						}}
						validate={({name, description}) => ({
							name: name ? undefined : t('admin.tenants.tenantNameRequiredError'),
							description:
								description !== undefined && description.length > MAX_DESCRIPTION_LENGTH
									? t('admin.tenants.descriptionTooLongError', {max: MAX_DESCRIPTION_LENGTH})
									: undefined,
						})}
					>
						{({handleSubmit, form}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('admin.tenants.editTenantModalTitle')}</DialogTitle>
									<Button
										type="button"
										variant="ghost"
										size="icon-sm"
										className="absolute top-2 right-2"
										aria-label={t('admin.tenants.cancelButton')}
										onClick={onClose}
									>
										<X aria-hidden />
									</Button>
								</DialogHeader>
								<DialogBody>
									<form onSubmit={handleSubmit} className="flex flex-col gap-4">
										<div className="flex flex-col gap-1.5">
											<Label htmlFor="tenantId">{t('admin.tenants.tenantIdFieldLabel')}</Label>
											<Input
												id="tenantId"
												value={tenant.tenantId}
												helperText={t('admin.tenants.tenantIdHelperText')}
												disabled
												readOnly
											/>
										</div>
										<Field name="name">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="tenantName">{t('admin.tenants.tenantNameFieldLabel')}</Label>
													<Input
														id="tenantName"
														placeholder={t('admin.tenants.tenantNameFieldPlaceholder')}
														value={input.value}
														onChange={input.onChange}
														autoFocus
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
													/>
												</div>
											)}
										</Field>
										<Field name="description">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="tenantDescription">{t('admin.tenants.descriptionFieldLabel')}</Label>
													<Textarea
														id="tenantDescription"
														placeholder={t('admin.tenants.descriptionFieldPlaceholder', {max: MAX_DESCRIPTION_LENGTH})}
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
										{t('admin.tenants.cancelButton')}
									</Button>
									<Button type="button" loading={update.isPending} onClick={form.submit}>
										{t('admin.tenants.saveButton')}
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

export {EditTenantModal};
