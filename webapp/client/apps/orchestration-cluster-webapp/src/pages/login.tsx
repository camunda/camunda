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
import {TextInput, PasswordInput, Column, Grid, Stack, InlineNotification, Button} from '@carbon/react';
import {CamundaLogo} from '#/modules/login/components/CamundaLogo';
import {getCurrentCopyrightNoticeText} from '#/modules/login/getCurrentCopyrightNoticeText';
import {Disclaimer} from '#/modules/login/components/Disclaimer';
import {LoadingSpinner} from '#/modules/login/components/LoadingSpinner';
import {authenticationStore} from '#/modules/auth/stores/authentication';
import styles from './login.module.scss';

type FormValues = {
	username: string;
	password: string;
};

const Login: React.FC = () => {
	const router = useRouter();

	return (
		<Grid as="main" condensed className={styles['container']!}>
			<Form<FormValues>
				onSubmit={async ({username, password}) => {
					try {
						const {error} = await authenticationStore.handleLogin(username, password);

						if (error === null) {
							// Re-triggers /login beforeLoad, which detects the active session and redirects
							await router.invalidate();
							return;
						}

						if (error.variant === 'failed-response' && error.response.status === 401) {
							return {
								// TODO: replace with i18n: https://github.com/camunda/camunda/issues/51325
								[FORM_ERROR]: 'Username and password do not match.',
							};
						}

						return {
							// TODO: replace with i18n: https://github.com/camunda/camunda/issues/51325
							[FORM_ERROR]: 'Credentials could not be verified. Please try again.',
						};
					} catch {
						return {
							// TODO: replace with i18n: https://github.com/camunda/camunda/issues/51325
							[FORM_ERROR]: 'Credentials could not be verified. Please try again.',
						};
					}
				}}
				validate={({username, password}) => {
					const errors: {username?: string; password?: string} = {};

					if (!username) {
						// TODO: replace with i18n: https://github.com/camunda/camunda/issues/51325
						errors.username = 'Username is required.';
					}

					if (!password) {
						// TODO: replace with i18n: https://github.com/camunda/camunda/issues/51325
						errors.password = 'Password is required.';
					}

					return errors;
				}}
			>
				{({handleSubmit, submitError, submitting}) => {
					return (
						<Column
							as="form"
							sm={4}
							md={{
								span: 4,
								offset: 2,
							}}
							lg={{
								span: 6,
								offset: 5,
							}}
							xlg={{
								span: 4,
								offset: 6,
							}}
							onSubmit={handleSubmit}
						>
							<Stack>
								<div className={styles['logo']}>
									{/* TODO: replace with i18n: https://github.com/camunda/camunda/issues/51325 */}
									<CamundaLogo aria-label="Camunda logo" />
								</div>
							</Stack>
							<Stack gap={3}>
								<div className={styles['error']}>
									{submitError && <InlineNotification title={submitError} hideCloseButton kind="error" role="alert" />}
								</div>
								<div className={styles['field']}>
									<Field<FormValues['username']> name="username" type="text">
										{({input, meta}) => (
											<TextInput
												{...input}
												name={input.name}
												id={input.name}
												onChange={input.onChange}
												labelText="Username"
												invalid={meta.error && meta.touched}
												invalidText={meta.error}
												placeholder="Username"
											/>
										)}
									</Field>
								</div>
								<div className={styles['field']}>
									<Field<FormValues['password']> name="password" type="password">
										{({input, meta}) => (
											<PasswordInput
												name={input.name}
												id={input.name}
												onChange={input.onChange}
												onBlur={input.onBlur}
												onFocus={input.onFocus}
												value={input.value}
												type="password"
												// TODO: replace with i18n: https://github.com/camunda/camunda/issues/51325
												hidePasswordLabel="Hide password"
												showPasswordLabel="Show password"
												labelText="Password"
												invalid={meta.error && meta.touched}
												invalidText={meta.error}
												placeholder="Password"
											/>
										)}
									</Field>
								</div>
								<Button
									type="submit"
									disabled={submitting}
									renderIcon={submitting ? LoadingSpinner : undefined}
									className={styles['button']!}
								>
									{/* TODO: replace with i18n: https://github.com/camunda/camunda/issues/51325 */}
									{submitting ? 'Logging in...' : 'Login'}
								</Button>
								<Disclaimer />
							</Stack>
						</Column>
					);
				}}
			</Form>
			<Column sm={4} md={8} lg={16} as="span" className={styles['copyrightNotice']!}>
				{getCurrentCopyrightNoticeText()}
			</Column>
		</Grid>
	);
};

export {Login};
