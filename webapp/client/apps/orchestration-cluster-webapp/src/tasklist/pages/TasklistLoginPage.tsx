/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {FORM_ERROR} from 'final-form';
import {Field, Form} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {useRouter} from '@tanstack/react-router';
import {
	Alert,
	Button,
	CamundaLogo,
	Card,
	CardContent,
	CardHeader,
	Heading,
	Input,
	Label,
	Text,
} from '@camunda/design-system';
import {Eye, EyeOff} from 'lucide-react';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {getCurrentCopyrightNoticeText} from '#/shared/login/getCurrentCopyrightNoticeText';
import styles from './TasklistLoginPage.module.scss';

type FormValues = {
	username: string;
	password: string;
};

const TasklistLoginPage: React.FC = () => {
	const router = useRouter();
	const {t} = useTranslation();
	const [isPasswordVisible, setIsPasswordVisible] = useState(false);

	return (
		<main className={styles.page}>
			<Card className={styles.card}>
				<CardHeader className={styles.header}>
					<div className={styles.logo} role="img" aria-label={t('loginLogoLabel')}>
						<CamundaLogo className={styles.logoMark} />
					</div>
					<Heading as="h1" variant="heading-lg">
						Tasklist
					</Heading>
				</CardHeader>
				<CardContent>
					<Form<FormValues>
						onSubmit={async ({username, password}) => {
							try {
								const {error} = await authenticationStore.handleLogin(username, password);

								if (error === null) {
									await router.invalidate();
									return;
								}

								return {
									[FORM_ERROR]:
										error.variant === 'failed-response' && error.response.status === 401
											? t('loginErrorUsernamePasswordMismatch')
											: t('loginErrorCredentialsNotVerified'),
								};
							} catch {
								return {[FORM_ERROR]: t('loginErrorCredentialsNotVerified')};
							}
						}}
						validate={({username, password}) => ({
							username: username ? undefined : t('loginErrorUsernameRequired'),
							password: password ? undefined : t('loginErrorPasswordRequired'),
						})}
					>
						{({handleSubmit, submitError, submitting}) => (
							<form className={styles.form} onSubmit={handleSubmit} noValidate>
								{submitError ? <Alert variant="destructive" title={submitError} /> : null}

								<Field<FormValues['username']> name="username">
									{({input, meta}) => {
										const isInvalid = meta.touched && meta.error !== undefined;

										return (
											<div className={styles.field}>
												<Label htmlFor="username">{t('loginUsernameFieldLabel')}</Label>
												<Input
													{...input}
													id="username"
													autoComplete="username"
													placeholder={t('loginUsernameFieldPlaceholder')}
													aria-invalid={isInvalid}
													aria-errormessage={isInvalid ? 'username-error' : undefined}
												/>
												{isInvalid ? (
													<Text id="username-error" as="p" variant="helper" className={styles.fieldError}>
														{meta.error}
													</Text>
												) : null}
											</div>
										);
									}}
								</Field>

								<Field<FormValues['password']> name="password">
									{({input, meta}) => {
										const isInvalid = meta.touched && meta.error !== undefined;

										return (
											<div className={styles.field}>
												<Label htmlFor="password">{t('loginPasswordFieldLabel')}</Label>
												<div className={styles.passwordInput}>
													<Input
														{...input}
														id="password"
														type={isPasswordVisible ? 'text' : 'password'}
														autoComplete="current-password"
														placeholder={t('loginPasswordFieldPlaceholder')}
														aria-invalid={isInvalid}
														aria-errormessage={isInvalid ? 'password-error' : undefined}
													/>
													<Button
														type="button"
														variant="ghost"
														size="icon-sm"
														className={styles.passwordToggle}
														aria-label={t(
															isPasswordVisible ? 'loginHidePasswordButtonLabel' : 'loginShowPasswordButtonLabel',
														)}
														onClick={() => setIsPasswordVisible((isVisible) => !isVisible)}
													>
														{isPasswordVisible ? <EyeOff aria-hidden /> : <Eye aria-hidden />}
													</Button>
												</div>
												{isInvalid ? (
													<Text id="password-error" as="p" variant="helper" className={styles.fieldError}>
														{meta.error}
													</Text>
												) : null}
											</div>
										);
									}}
								</Field>

								<Button type="submit" size="lg" loading={submitting} className={styles.submitButton}>
									{submitting ? t('loginLoggingInMessage') : t('loginButtonLabel')}
								</Button>
							</form>
						)}
					</Form>
					<Disclaimer />
				</CardContent>
			</Card>
			<Text as="p" variant="helper" className={styles.copyrightNotice}>
				{getCurrentCopyrightNoticeText()}
			</Text>
		</main>
	);
};

function Disclaimer() {
	if (getBootConfig().isEnterprise) {
		return null;
	}

	return (
		<Text as="p" variant="helper" className={styles.disclaimer}>
			Non-Production License. If you would like information on production usage, please refer to our{' '}
			<a href="https://legal.camunda.com/#self-managed-non-production-terms" target="_blank" rel="noreferrer">
				terms &amp; conditions page
			</a>{' '}
			or{' '}
			<a href="https://camunda.com/contact/" target="_blank" rel="noreferrer">
				contact sales
			</a>
			.
		</Text>
	);
}

export {TasklistLoginPage};
