/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useRouter} from '@tanstack/react-router';
import {Form, Field} from 'react-final-form';
import {FORM_ERROR} from 'final-form';
import {Eye, EyeOff} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {Alert, Button, CamundaLogo, Card, CardContent, Heading, Input, Label, Text} from '@camunda/design-system';
import {TextInput} from '@camunda/design-system/carbon-compat';
import {getCurrentCopyrightNoticeText} from '#/shared/login/getCurrentCopyrightNoticeText';
import {Disclaimer} from '#/shared/login/shadcn.components/Disclaimer';
import {authenticationStore} from '#/shared/auth/authentication.store';
import styles from './TasklistLoginPage.module.scss';

type FormValues = {
	username: string;
	password: string;
};

type PasswordFieldProps = {
	id: string;
	name: string;
	value: string;
	onChange: React.ChangeEventHandler<HTMLInputElement>;
	onBlur: React.FocusEventHandler<HTMLInputElement>;
	onFocus: React.FocusEventHandler<HTMLInputElement>;
	labelText: string;
	placeholder: string;
	invalid: boolean;
	invalidText?: string;
	hideLabelText: string;
	showLabelText: string;
};

// @camunda/design-system has no password input yet, and its carbon-compat
// PasswordInput is a raw `@carbon/react` re-export, so neither can be used
// on a Carbon-free page. Composed by hand from the DS Input + Label + a
// Button icon toggle instead.
const PasswordField: React.FC<PasswordFieldProps> = ({
	id,
	name,
	value,
	onChange,
	onBlur,
	onFocus,
	labelText,
	placeholder,
	invalid,
	invalidText,
	hideLabelText,
	showLabelText,
}) => {
	const [isVisible, setIsVisible] = useState(false);
	const errorId = `${id}-error`;

	return (
		<div className={styles.passwordField}>
			<Label htmlFor={id}>{labelText}</Label>
			<div className={styles.passwordInputWrapper}>
				<Input
					id={id}
					name={name}
					type={isVisible ? 'text' : 'password'}
					value={value}
					onChange={onChange}
					onBlur={onBlur}
					onFocus={onFocus}
					placeholder={placeholder}
					aria-invalid={invalid || undefined}
					aria-errormessage={invalid ? errorId : undefined}
					className={styles.passwordInput}
				/>
				<Button
					type="button"
					variant="ghost"
					size="icon"
					className={styles.passwordToggle}
					aria-label={isVisible ? hideLabelText : showLabelText}
					onClick={() => {
						setIsVisible((visible) => !visible);
					}}
				>
					{isVisible ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
				</Button>
			</div>
			{invalid && invalidText !== undefined ? (
				<Text id={errorId} as="p" variant="label-sm" className={styles.fieldError}>
					{invalidText}
				</Text>
			) : null}
		</div>
	);
};

const TasklistLoginPage: React.FC = () => {
	const router = useRouter();
	const {t} = useTranslation();

	return (
		<div className={styles.page}>
			<main className={styles.content}>
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
						<form className={styles.formWrapper} onSubmit={handleSubmit}>
							<Card>
								<CardContent className={styles.cardContent}>
									<div className={styles.logo}>
										<CamundaLogo className={styles.logoMark} />
									</div>
									<Heading as="h1" variant="heading-lg" className={styles.title}>
										Tasklist
									</Heading>
									{submitError !== undefined ? <Alert variant="destructive" title={submitError} /> : null}
									<div className={styles.field}>
										<Field<FormValues['username']> name="username" type="text">
											{({input, meta}) => (
												<TextInput
													{...input}
													name={input.name}
													id={input.name}
													onChange={input.onChange}
													labelText={t('loginUsernameFieldLabel')}
													invalid={Boolean(meta.error && meta.touched)}
													invalidText={meta.error}
													placeholder={t('loginUsernameFieldPlaceholder')}
												/>
											)}
										</Field>
									</div>
									<div className={styles.field}>
										<Field<FormValues['password']> name="password" type="password">
											{({input, meta}) => (
												<PasswordField
													id={input.name}
													name={input.name}
													value={input.value}
													onChange={input.onChange}
													onBlur={input.onBlur}
													onFocus={input.onFocus}
													labelText={t('loginPasswordFieldLabel')}
													placeholder={t('loginPasswordFieldPlaceholder')}
													invalid={Boolean(meta.error && meta.touched)}
													invalidText={meta.error}
													hideLabelText={t('loginHidePasswordButtonLabel')}
													showLabelText={t('loginShowPasswordButtonLabel')}
												/>
											)}
										</Field>
									</div>
									<Button type="submit" size="lg" loading={submitting} className={styles.button}>
										{submitting ? t('loginLoggingInMessage') : t('loginButtonLabel')}
									</Button>
									<Disclaimer />
								</CardContent>
							</Card>
						</form>
					)}
				</Form>
			</main>
			<Text as="span" variant="helper" className={styles.copyrightNotice}>
				{getCurrentCopyrightNoticeText()}
			</Text>
		</div>
	);
};

export {TasklistLoginPage};
