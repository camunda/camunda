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
import {isValidEmail, isValidUsername} from './userValidation';
import {useUserMutations} from './useUserMutations';

type FormValues = {
	username: string;
	name: string;
	email: string;
	password: string;
	confirmPassword: string;
};

type Props = {
	isOpen: boolean;
	onClose: () => void;
};

const CreateUserModal: React.FC<Props> = ({isOpen, onClose}) => {
	const {t} = useTranslation();
	const {create} = useUserMutations();

	const handleSubmit = (values: FormValues) => {
		create.mutate(
			{username: values.username, name: values.name, email: values.email, password: values.password},
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
				aria-label={t('admin.users.createUser')}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				{isOpen ? (
					<Form<FormValues>
						onSubmit={handleSubmit}
						validate={(values) => {
							const errors: Partial<Record<keyof FormValues, string>> = {};

							if (!values.username) {
								errors.username = t('admin.users.usernameRequired');
							} else if (!isValidUsername(values.username)) {
								errors.username = t('admin.users.usernameInvalid');
							}

							if (values.email && !isValidEmail(values.email)) {
								errors.email = t('admin.users.emailInvalid');
							}

							if (!values.password) {
								errors.password = t('admin.users.passwordRequired');
							}

							if (!values.confirmPassword) {
								errors.confirmPassword = t('admin.users.confirmPasswordRequired');
							} else if (values.confirmPassword !== values.password) {
								errors.confirmPassword = t('admin.users.passwordsDoNotMatch');
							}

							return errors;
						}}
					>
						{({handleSubmit: handleFormSubmit, form}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('admin.users.createUser')}</DialogTitle>
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
										<Field name="username" required>
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="create-user-username">{t('admin.users.username')}</Label>
													<Input
														id="create-user-username"
														placeholder={t('admin.users.enterUsername')}
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
										<Field name="name">
											{({input}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="create-user-name">{t('admin.users.name')}</Label>
													<Input
														id="create-user-name"
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
													<Label htmlFor="create-user-email">{t('admin.users.email')}</Label>
													<Input
														id="create-user-email"
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
										<Field name="password">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="create-user-password">{t('admin.users.password')}</Label>
													<Input
														id="create-user-password"
														type="password"
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
										<Field name="confirmPassword">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="create-user-confirm-password">{t('admin.users.confirmPassword')}</Label>
													<Input
														id="create-user-confirm-password"
														type="password"
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
									</form>
								</DialogBody>
								<DialogFooter>
									<Button type="button" variant="secondary" onClick={onClose}>
										{t('admin.users.cancel')}
									</Button>
									<Button type="button" onClick={form.submit} disabled={create.isPending}>
										{t('admin.users.createUser')}
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

export {CreateUserModal};
