/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRouter} from '@tanstack/react-router';
import {Form, useField} from 'react-final-form';
import {FORM_ERROR} from 'final-form';
import {useTranslation} from 'react-i18next';
import {LoginScreen} from '@camunda/design-system';
import {getCurrentCopyrightNoticeText} from '#/shared/login/getCurrentCopyrightNoticeText';
import {Disclaimer} from '#/shared/login/shadcn.components/Disclaimer';
import {authenticationStore} from '#/shared/auth/authentication.store';

type FormValues = {
	username: string;
	password: string;
};

type Props = {
	title: string;
};

const LoginPageForm: React.FC<{
	title: string;
	handleSubmit: () => void;
	submitError?: string;
	submitting?: boolean;
}> = ({title, handleSubmit, submitError, submitting = false}) => {
	const {t} = useTranslation();
	const username = useField<FormValues['username']>('username', {type: 'text'});
	const password = useField<FormValues['password']>('password', {type: 'password'});
	const isUsernameInvalid = Boolean(username.meta.error && username.meta.touched);
	const isPasswordInvalid = Boolean(password.meta.error && password.meta.touched);

	return (
		<LoginScreen
			title={title}
			formLabel={t('loginButtonLabel')}
			onSubmit={handleSubmit}
			submitting={submitting}
			submitLabel={t('loginButtonLabel')}
			submittingLabel={t('loginLoggingInMessage')}
			showPasswordLabel={t('loginShowPasswordButtonLabel')}
			hidePasswordLabel={t('loginHidePasswordButtonLabel')}
			error={submitError}
			usernameInputProps={{
				...username.input,
				label: t('loginUsernameFieldLabel'),
				placeholder: t('loginUsernameFieldPlaceholder'),
				// aria-required only: an actual `required` attribute would trigger native
				// browser validation and block final-form's own submit/validation flow.
				'aria-required': true,
				'aria-invalid': isUsernameInvalid,
				invalidText: isUsernameInvalid ? username.meta.error : undefined,
			}}
			passwordInputProps={{
				...password.input,
				label: t('loginPasswordFieldLabel'),
				placeholder: t('loginPasswordFieldPlaceholder'),
				'aria-required': true,
				'aria-invalid': isPasswordInvalid,
				invalidText: isPasswordInvalid ? password.meta.error : undefined,
			}}
			legal={<Disclaimer />}
			footer={getCurrentCopyrightNoticeText()}
		/>
	);
};

const LoginPage: React.FC<Props> = ({title}) => {
	const router = useRouter();
	const {t} = useTranslation();

	return (
		<Form<FormValues>
			onSubmit={async ({username, password}) => {
				try {
					const {error} = await authenticationStore.handleLogin(username, password);

					if (error === null) {
						// Re-triggers the login route, which detects the active session and redirects
						await router.invalidate();
						return;
					}

					if (error.variant === 'failed-response' && error.response.status === 401) {
						return {
							[FORM_ERROR]: t('loginErrorUsernamePasswordMismatch'),
						};
					}

					return {
						[FORM_ERROR]: t('loginErrorCredentialsNotVerified'),
					};
				} catch {
					return {
						[FORM_ERROR]: t('loginErrorCredentialsNotVerified'),
					};
				}
			}}
			validate={({username, password}) => {
				const errors: {username?: string; password?: string} = {};

				if (!username) {
					errors.username = t('loginErrorUsernameRequired');
				}

				if (!password) {
					errors.password = t('loginErrorPasswordRequired');
				}

				return errors;
			}}
		>
			{({handleSubmit, submitError, submitting}) => (
				<LoginPageForm title={title} handleSubmit={handleSubmit} submitError={submitError} submitting={submitting} />
			)}
		</Form>
	);
};

export {LoginPage};
