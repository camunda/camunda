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
import {Eye, EyeOff, Loader2} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {Alert, Button, CamundaLogo, Card, CardContent, Input, Label} from '@camunda/design-system';
import {TextInput} from '@camunda/design-system/carbon-compat';
import {getCurrentCopyrightNoticeText} from '#/shared/login/getCurrentCopyrightNoticeText';
import {Disclaimer} from '#/shared/login/components/Disclaimer';
import {authenticationStore} from '#/shared/auth/authentication.store';
import styles from './LoginPageDS.module.scss';

type FormValues = {
	username: string;
	password: string;
};

type Props = {
	title: string;
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

// DS-only password field. carbon-compat's `PasswordInput` is a SHIM — a bare
// re-export of Carbon's own component (see carbon-compat/password-input.tsx,
// "MIGRATION TODO: PasswordInput shadcn component not yet implemented") — so
// importing it here would embed raw `cds--*` Carbon markup inside the DS
// Card. Composed by hand from the DS `Input` + a lucide `Eye`/`EyeOff`
// toggle instead, same "compose from primitives when the packaged component
// doesn't support what's needed" precedent AccountMenu.tsx set for the user
// menu, and following the same label/error layout carbon-compat's own
// `TextInput` adapter uses (see carbon-compat/text-input.tsx) so the two
// fields read consistently.
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

	return (
		<div className={styles.passwordFieldDS}>
			<Label htmlFor={id} className={styles.labelDS}>
				{labelText}
			</Label>
			<div className={styles.passwordInputWrapperDS}>
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
					className={styles.passwordInputDS}
				/>
				<button
					type="button"
					className={styles.passwordToggleDS}
					aria-label={isVisible ? hideLabelText : showLabelText}
					onClick={() => {
						setIsVisible((visible) => !visible);
					}}
				>
					{isVisible ? (
						<EyeOff aria-hidden="true" className={styles.passwordToggleIconDS} />
					) : (
						<Eye aria-hidden="true" className={styles.passwordToggleIconDS} />
					)}
				</button>
			</div>
			{invalid && invalidText !== undefined ? <p className={styles.fieldErrorDS}>{invalidText}</p> : null}
		</div>
	);
};

// DS-only login page. Rendered only when title === 'Tasklist' and
// featureFlags.dsTasklistUI is on — see LoginPage.tsx for the gate. Operate
// and Admin (title undefined) always keep the Carbon page below that gate,
// byte-for-byte unchanged, same as Header.tsx keeping Operate/Admin on the
// Carbon header while Tasklist routes get TasklistDSHeader.tsx.
//
// The logo, title, and form fields are wrapped in a single DS `Card` per the
// design requirement to present the login panel as one raised tile rather
// than bare text on the page background, the same wrapping TaskDetailsForm.tsx
// uses for the form-js panel. The page background outside the card uses the
// DS `--background` token via `.pageDS` in LoginPageDS.module.scss, matching
// TasklistProcessesPage.module.scss's `.pageDS` convention.
const LoginPageDS: React.FC<Props> = ({title}) => {
	const router = useRouter();
	const {t} = useTranslation();

	return (
		<div className={styles.pageDS}>
			<main className={styles.contentDS}>
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
						<form className={styles.formWrapperDS} onSubmit={handleSubmit}>
							<Card>
								<CardContent className={styles.cardContentDS}>
									<div className={styles.logoDS}>
										{/* The same mark AppHeader renders by default (TasklistDSHeader.tsx
										    passes no `logo` override) — fills use DS foreground/background
										    tokens, so it themes correctly in dark mode. Aria-hidden already,
										    same as in the header; unlike the old wordmark it carries no
										    separate label. */}
										<CamundaLogo className={styles.logoMarkDS} />
									</div>
									<h1 className={styles.titleDS}>{title}</h1>
									{submitError !== undefined ? <Alert variant="destructive" title={submitError} /> : null}
									<div className={styles.fieldDS}>
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
									<div className={styles.fieldDS}>
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
									{/* The installed @camunda/design-system version's `Button` has no
									    `loading` prop yet (that's a newer API) — `disabled` + a manual
									    spinner reproduces the Carbon button's `disabled`/`renderIcon`
									    loading treatment instead. */}
									<Button
										type="submit"
										disabled={submitting}
										aria-busy={submitting || undefined}
										className={styles.buttonDS}
									>
										{submitting ? (
											<>
												<Loader2 aria-hidden="true" className={styles.buttonSpinnerDS} />
												{t('loginLoggingInMessage')}
											</>
										) : (
											t('loginButtonLabel')
										)}
									</Button>
									<Disclaimer />
								</CardContent>
							</Card>
						</form>
					)}
				</Form>
			</main>
			<span className={styles.copyrightNoticeDS}>{getCurrentCopyrightNoticeText()}</span>
		</div>
	);
};

export {LoginPageDS};
