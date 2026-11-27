/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRouter} from '@tanstack/react-router';
import {Field, Form} from 'react-final-form';
import {FORM_ERROR} from 'final-form';
import {useTranslation} from 'react-i18next';
import {Alert, Button, CamundaLogo, Card, CardContent, Heading, Input, Label, Separator} from '@camunda/design-system';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import {getErrorMessage} from '#/shared/http/getErrorMessage';
import {isValidEmail} from '#/admin/modules/users/userValidation';

type FormValues = {
	username: string;
	password: string;
	confirmPassword: string;
	name: string;
	email: string;
};

const MIN_PASSWORD_LENGTH = 6;

function isValidPassword(password: string): boolean {
	return password.length >= MIN_PASSWORD_LENGTH && /\d/.test(password);
}

const FieldError: React.FC<{id: string; error?: string}> = ({id, error}) =>
	error === undefined ? null : (
		<p id={id} role="alert" className="text-xs text-danger-action-default">
			{error}
		</p>
	);

const AdminSetupPage: React.FC = () => {
	const router = useRouter();
	const {t} = useTranslation();

	const renderField = (
		name: keyof FormValues,
		{
			type = 'text',
			isRequired = false,
			hint,
			autoComplete,
		}: {type?: 'text' | 'password' | 'email'; isRequired?: boolean; hint?: string; autoComplete?: string},
	) => (
		<Field<string> name={name}>
			{({input, meta}) => {
				const isInvalid = Boolean(meta.error && meta.touched);
				const errorId = `${name}-error`;
				const hintId = `${name}-hint`;
				const describedBy = [hint === undefined ? undefined : hintId, isInvalid ? errorId : undefined]
					.filter(Boolean)
					.join(' ');

				return (
					<div className="flex flex-col gap-1.5">
						<Label htmlFor={name}>
							{t(`admin.setup.${name}`)}
							{isRequired ? (
								<span className="ml-1 text-danger-action-default" aria-hidden="true">
									*
								</span>
							) : null}
						</Label>
						<Input
							{...input}
							id={name}
							type={type}
							autoComplete={autoComplete}
							placeholder={t(`admin.setup.${name}Placeholder`)}
							aria-required={isRequired}
							aria-invalid={isInvalid}
							aria-describedby={describedBy || undefined}
						/>
						{hint === undefined ? null : (
							<p id={hintId} className="text-xs text-text-secondary">
								{hint}
							</p>
						)}
						<FieldError id={errorId} error={isInvalid ? meta.error : undefined} />
					</div>
				);
			}}
		</Field>
	);

	return (
		<div className="flex min-h-dvh flex-col items-center bg-background p-4">
			<main className="flex w-full flex-1 items-center justify-center">
				<Form<FormValues>
					validateOnBlur
					onSubmit={async ({username, password, name, email}) => {
						const {error} = await request(
							endpoints.createAdminUser({
								username: username.trim(),
								password,
								name: name?.trim() || undefined,
								email: email?.trim() || undefined,
							}),
							{
								skipSessionCheck: true,
							},
						);

						if (error === null) {
							await router.navigate({to: '/admin/login'});
							return;
						}

						return {[FORM_ERROR]: (await getErrorMessage(error)) ?? t('admin.setup.genericError')};
					}}
					validate={({username, password, confirmPassword, email}) => {
						const errors: Partial<Record<keyof FormValues, string>> = {};

						if (!username?.trim()) {
							errors.username = t('admin.setup.usernameRequired');
						}

						if (!password) {
							errors.password = t('admin.setup.passwordRequired');
						} else if (!isValidPassword(password)) {
							errors.password = t('admin.setup.passwordRequirements');
						}

						if (!confirmPassword) {
							errors.confirmPassword = t('admin.setup.confirmPasswordRequired');
						} else if (confirmPassword !== password) {
							errors.confirmPassword = t('admin.setup.passwordsDoNotMatch');
						}

						if (email && !isValidEmail(email)) {
							errors.email = t('admin.setup.emailInvalid');
						}

						return errors;
					}}
				>
					{({handleSubmit, submitError, submitting}) => (
						<form className="w-full max-w-104" onSubmit={handleSubmit} noValidate>
							<Card>
								<CardContent className="flex flex-col gap-4">
									<div className="flex justify-center pt-2">
										<CamundaLogo className="size-12" />
									</div>
									<Heading as="h1" variant="heading-lg" className="mb-2 pb-2 text-center">
										{t('admin.setup.title')}
									</Heading>
									{submitError === undefined ? null : <Alert variant="destructive" title={submitError} />}
									{renderField('username', {isRequired: true, autoComplete: 'username'})}
									{renderField('password', {
										type: 'password',
										isRequired: true,
										hint: t('admin.setup.passwordRequirements'),
										autoComplete: 'new-password',
									})}
									{renderField('confirmPassword', {type: 'password', isRequired: true, autoComplete: 'new-password'})}
									<Separator />
									{renderField('name', {})}
									{renderField('email', {type: 'email'})}
									<Button type="submit" disabled={submitting} aria-busy={submitting}>
										{submitting ? t('admin.setup.submitting') : t('admin.setup.submit')}
									</Button>
								</CardContent>
							</Card>
						</form>
					)}
				</Form>
			</main>
		</div>
	);
};

export {AdminSetupPage};
