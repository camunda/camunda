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
	Heading,
	Input,
	Label,
	Separator,
	Text,
} from '@camunda/design-system';
import type {User} from '@camunda/camunda-api-zod-schemas/8.11';
import {isValidEmail} from '#/admin/modules/users/userValidation';
import {useUserMutations} from '#/admin/modules/users/useUserMutations';

type FormValues = {
	name: string;
	email: string;
	password: string;
	confirmPassword: string;
};

type Props = {
	isOpen: boolean;
	user: User;
	onClose: () => void;
};

const EditUserModal: React.FC<Props> = ({isOpen, user, onClose}) => {
	const {t} = useTranslation();
	const {update} = useUserMutations();

	const handleSubmit = (values: FormValues) => {
		update.mutate(
			{
				username: user.username,
				name: values.name,
				email: values.email,
				...(values.password ? {password: values.password} : {}),
			},
			{onSuccess: onClose},
		);
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
				aria-label={t('admin.users.editUserWithName', {username: user.username})}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				{isOpen ? (
					<Form<FormValues>
						initialValues={{name: user.name ?? '', email: user.email ?? '', password: '', confirmPassword: ''}}
						onSubmit={handleSubmit}
						validate={(values) => {
							const errors: Partial<Record<keyof FormValues, string>> = {};

							if (values.email && !isValidEmail(values.email)) {
								errors.email = t('admin.users.emailInvalid');
							}

							if (values.password && !values.confirmPassword) {
								errors.confirmPassword = t('admin.users.confirmPasswordRequired');
							} else if (values.confirmPassword && values.confirmPassword !== values.password) {
								errors.confirmPassword = t('admin.users.passwordsDoNotMatch');
							}

							return errors;
						}}
					>
						{({handleSubmit: handleFormSubmit, form}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('admin.users.editUserWithName', {username: user.username})}</DialogTitle>
									<Button
										type="button"
										variant="ghost"
										size="icon-sm"
										className="absolute top-2 right-2"
										aria-label={t('admin.users.cancel')}
										onClick={onClose}
									>
										<X aria-hidden />
									</Button>
								</DialogHeader>
								<DialogBody>
									<form className="flex flex-col gap-4" onSubmit={handleFormSubmit}>
										<Field name="name">
											{({input}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="edit-user-name">{t('admin.users.name')}</Label>
													<Input
														id="edit-user-name"
														autoFocus
														placeholder={t('admin.users.enterName')}
														value={input.value}
														onChange={input.onChange}
														onBlur={input.onBlur}
													/>
												</div>
											)}
										</Field>
										<Field name="email">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="edit-user-email">{t('admin.users.email')}</Label>
													<Input
														id="edit-user-email"
														type="email"
														placeholder={t('admin.users.enterEmail')}
														value={input.value}
														onChange={input.onChange}
														onBlur={input.onBlur}
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
													/>
												</div>
											)}
										</Field>
										<Separator />
										<div className="flex flex-col gap-1">
											<Heading as="h3" variant="heading-xs">
												{t('admin.users.resetPassword')}
											</Heading>
											<Text as="p">{t('admin.users.resetPasswordInfo')}</Text>
										</div>
										<Field name="password">
											{({input}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="edit-user-password">{t('admin.users.newPassword')}</Label>
													<Input
														id="edit-user-password"
														type="password"
														placeholder={t('admin.users.enterNewPassword')}
														value={input.value}
														onChange={input.onChange}
														onBlur={input.onBlur}
														helperText={t('admin.users.keepCurrentPassword')}
													/>
												</div>
											)}
										</Field>
										<Field name="confirmPassword">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="edit-user-confirm-password">{t('admin.users.confirmPassword')}</Label>
													<Input
														id="edit-user-confirm-password"
														type="password"
														value={input.value}
														onChange={input.onChange}
														onBlur={input.onBlur}
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
										{t('admin.users.cancel')}
									</Button>
									<Button type="button" onClick={form.submit} disabled={update.isPending}>
										{t('admin.users.updateUser')}
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

export {EditUserModal};
