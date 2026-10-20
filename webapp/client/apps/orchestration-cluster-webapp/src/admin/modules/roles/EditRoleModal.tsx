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
import type {Role} from '@camunda/camunda-api-zod-schemas/8.11';
import {useRoleMutations} from './useRoleMutations';

type FormValues = {
	name: string;
	description: string;
};

type Props = {
	isOpen: boolean;
	role: Role;
	onClose: () => void;
};

const EditRoleModal: React.FC<Props> = ({isOpen, role, onClose}) => {
	const {t} = useTranslation();
	const {update} = useRoleMutations();

	const handleSubmit = (values: FormValues) => {
		update.mutate({roleId: role.roleId, name: values.name, description: values.description}, {onSuccess: onClose});
	};

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
				aria-label={t('admin.roles.editRole')}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				{isOpen ? (
					<Form<FormValues>
						initialValues={{name: role.name, description: role.description ?? ''}}
						onSubmit={handleSubmit}
						validate={({name}) => ({name: name ? undefined : t('admin.roles.roleNameRequired')})}
					>
						{({handleSubmit: handleFormSubmit, form}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('admin.roles.editRole')}</DialogTitle>
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
									<form className="flex flex-col gap-4" onSubmit={handleFormSubmit}>
										<div className="flex flex-col gap-1.5">
											<Label htmlFor="edit-role-id">{t('admin.roles.roleId')}</Label>
											<Input id="edit-role-id" value={role.roleId} readOnly />
										</div>
										<Field name="name">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="edit-role-name">{t('admin.roles.roleName')}</Label>
													<Input
														id="edit-role-name"
														placeholder={t('admin.roles.enterRoleName')}
														required
														autoFocus
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
													<Label htmlFor="edit-role-description">{t('admin.roles.description')}</Label>
													<Textarea
														id="edit-role-description"
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
									<Button type="button" loading={update.isPending} onClick={form.submit}>
										{t('admin.roles.updateRole')}
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

export {EditRoleModal};
