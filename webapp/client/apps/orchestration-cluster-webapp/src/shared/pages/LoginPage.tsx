/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRouter} from '@tanstack/react-router';
import {Form, Field} from 'react-final-form';
import {FORM_ERROR} from 'final-form';
import {TextInput, PasswordInput, InlineNotification, Button} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import {CamundaLogo} from '#/shared/login/components/CamundaLogo';
import {getCurrentCopyrightNoticeText} from '#/shared/login/getCurrentCopyrightNoticeText';
import {Disclaimer} from '#/shared/login/components/Disclaimer';
import {LoadingSpinner} from '#/shared/login/components/LoadingSpinner';
import {authenticationStore} from '#/shared/auth/authentication.store';
import styles from './LoginPage.module.scss';

type FormValues = {
	username: string;
	password: string;
};

type Props = {
	title?: string;
};

const LoginPage: React.FC<Props> = ({title}) => {
	const router = useRouter();
	const {t} = useTranslation();

	return (
		<div className={styles.page}>
			<main className={styles.main}>
				<div className={styles.content}>
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
						{({handleSubmit, submitError, submitting}) => {
							return (
								<form onSubmit={handleSubmit}>
									<div className={styles.card}>
										<div className={styles.logo}>
											<CamundaLogo aria-label={t('loginLogoLabel')} />
										</div>
										<h1 className={title === undefined ? 'cds--visually-hidden' : styles.title}>{title ?? 'Login'}</h1>
										{submitError && (
											<InlineNotification title={submitError} hideCloseButton kind="error" role="alert" lowContrast />
										)}
										<div className={styles.field}>
											<Field<FormValues['username']> name="username" type="text">
												{({input, meta}) => (
													<TextInput
														{...input}
														className={styles.field}
														name={input.name}
														id={input.name}
														onChange={input.onChange}
														labelText={t('loginUsernameFieldLabel')}
														invalid={meta.error && meta.touched}
														invalidText={meta.error}
														placeholder={t('loginUsernameFieldPlaceholder')}
													/>
												)}
											</Field>
										</div>
										<div className={styles.field}>
											<Field<FormValues['password']> name="password" type="password">
												{({input, meta}) => (
													<PasswordInput
														className={styles.field}
														name={input.name}
														id={input.name}
														onChange={input.onChange}
														onBlur={input.onBlur}
														onFocus={input.onFocus}
														value={input.value}
														type="password"
														hidePasswordLabel={t('loginHidePasswordButtonLabel')}
														showPasswordLabel={t('loginShowPasswordButtonLabel')}
														labelText={t('loginPasswordFieldLabel')}
														invalid={meta.error && meta.touched}
														invalidText={meta.error}
														placeholder={t('loginPasswordFieldPlaceholder')}
													/>
												)}
											</Field>
										</div>
										<Button
											type="submit"
											disabled={submitting}
											renderIcon={submitting ? LoadingSpinner : undefined}
											className={styles.button}
										>
											{submitting ? t('loginLoggingInMessage') : t('loginButtonLabel')}
										</Button>
										<Disclaimer />
									</div>
								</form>
							);
						}}
					</Form>
				</div>
			</main>
			<footer className={styles.footer}>{getCurrentCopyrightNoticeText()}</footer>
		</div>
	);
};

export {LoginPage};
