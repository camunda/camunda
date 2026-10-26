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
	Textarea,
} from '@camunda/design-system';
import {isValidId} from '#/admin/modules/shared/identifierPattern';
import {isDuplicateRoleIdError} from './isDuplicateRoleIdError';
import {useRoleMutations} from './useRoleMutations';

type FormValues = {
	roleId: string;
	name: string;
	description: string;
};

// The role ID is the only field that can also fail server-side (duplicate ID), surfaced via
// final-form's `submitError` rather than the synchronous `validate` error.
function getRoleIdError(meta: FieldMetaState<string>): string | undefined {
	if (meta.submitError && !meta.dirtySinceLastSubmit) {
		return meta.submitError;
	}
	return meta.touched ? meta.error : undefined;
}

type Props = {
	isOpen: boolean;
	onClose: () => void;
};

const AddRoleModal: React.FC<Props> = ({isOpen, onClose}) => {
	const {t} = useTranslation();
	const {create} = useRoleMutations();

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
				aria-label={t('admin.roles.createRole')}
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
								if (isDuplicateRoleIdError(error)) {
									return {roleId: t('admin.roles.roleIdAlreadyExists')};
								}
								return undefined;
							}
						}}
						validate={({roleId, name}) => ({
							roleId: !roleId
								? t('admin.roles.roleIdRequired')
								: isValidId(roleId)
									? undefined
									: t('admin.roles.roleIdInvalid'),
							name: name ? undefined : t('admin.roles.roleNameRequired'),
						})}
					>
						{({handleSubmit, form}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('admin.roles.createRole')}</DialogTitle>
									<Button
										type="button"
										variant="ghost"
										size="icon-sm"
										className="absolute top-2 right-2"
										aria-label={t('admin.roles.cancel')}
										onClick={onClose}
									>
										<X aria-hidden />
									</Button>
								</DialogHeader>
								<DialogBody>
									<form className="flex flex-col gap-4" onSubmit={handleSubmit}>
										<Field name="roleId">
											{({input, meta}) => {
												const errorMessage = getRoleIdError(meta);
												return (
													<div className="flex flex-col gap-1.5">
														<Label htmlFor="create-role-id">{t('admin.roles.roleId')}</Label>
														<Input
															id="create-role-id"
															placeholder={t('admin.roles.enterRoleId')}
															helperText={t('admin.roles.roleIdHelperText')}
															required
															autoFocus
															value={input.value}
															onChange={input.onChange}
															onBlur={input.onBlur}
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
													<Label htmlFor="create-role-name">{t('admin.roles.roleName')}</Label>
													<Input
														id="create-role-name"
														placeholder={t('admin.roles.enterRoleName')}
														required
														value={input.value}
														onChange={input.onChange}
														onBlur={input.onBlur}
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
													/>
												</div>
											)}
										</Field>
										<Field name="description">
											{({input}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="create-role-description">{t('admin.roles.description')}</Label>
													<Textarea
														id="create-role-description"
														placeholder={t('admin.roles.enterDescription')}
														value={input.value}
														onChange={input.onChange}
														onBlur={input.onBlur}
													/>
												</div>
											)}
										</Field>
									</form>
								</DialogBody>
								<DialogFooter>
									<Button type="button" variant="secondary" onClick={onClose}>
										{t('admin.roles.cancel')}
									</Button>
									<Button type="button" loading={create.isPending} onClick={form.submit}>
										{t('admin.roles.createRole')}
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

export {AddRoleModal};
