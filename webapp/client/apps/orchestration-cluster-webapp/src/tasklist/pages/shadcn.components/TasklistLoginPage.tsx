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
import {useTranslation} from 'react-i18next';
import {Alert, Button, CamundaLogo, Card, CardContent, Heading, Input, Label, Text} from '@camunda/design-system';
import {getCurrentCopyrightNoticeText} from '#/shared/login/getCurrentCopyrightNoticeText';
import {Disclaimer} from '#/shared/login/shadcn.components/Disclaimer';
import {authenticationStore} from '#/shared/auth/authentication.store';

type FormValues = {
	username: string;
	password: string;
};

const TasklistLoginPage: React.FC = () => {
	const router = useRouter();
	const {t} = useTranslation();

	return (
		<div className="flex min-h-full flex-col items-center bg-background p-4">
			<main className="flex w-full flex-1 items-center justify-center">
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
						<form className="w-full max-w-[26rem]" onSubmit={handleSubmit}>
							<Card>
								<CardContent className="flex flex-col gap-4">
									<div className="flex justify-center pt-2">
										<CamundaLogo className="size-12" />
									</div>
									<Heading as="h1" variant="heading-lg" className="mb-2 pb-2 text-center">
										Tasklist
									</Heading>
									{submitError !== undefined ? <Alert variant="destructive" title={submitError} /> : null}
									<Field<FormValues['username']> name="username" type="text">
										{({input, meta}) => {
											const isInvalid = Boolean(meta.error && meta.touched);
											const errorId = `${input.name}-error`;

											return (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor={input.name}>
														{t('loginUsernameFieldLabel')}
														<span className="ml-1 text-danger-action-default" aria-hidden="true">
															*
														</span>
													</Label>
													<Input
														{...input}
														id={input.name}
														placeholder={t('loginUsernameFieldPlaceholder')}
														aria-required="true"
														aria-invalid={isInvalid}
														aria-describedby={isInvalid ? errorId : undefined}
													/>
													{isInvalid && meta.error !== undefined ? (
														<p id={errorId} role="alert" className="text-xs text-danger-action-default">
															{meta.error}
														</p>
													) : null}
												</div>
											);
										}}
									</Field>
									<Field<FormValues['password']> name="password" type="password">
										{({input, meta}) => {
											const isInvalid = Boolean(meta.error && meta.touched);
											const errorId = `${input.name}-error`;

											return (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor={input.name}>
														{t('loginPasswordFieldLabel')}
														<span className="ml-1 text-danger-action-default" aria-hidden="true">
															*
														</span>
													</Label>
													<Input
														{...input}
														id={input.name}
														type="password"
														placeholder={t('loginPasswordFieldPlaceholder')}
														aria-required="true"
														aria-invalid={isInvalid}
														aria-describedby={isInvalid ? errorId : undefined}
													/>
													{isInvalid && meta.error !== undefined ? (
														<p id={errorId} role="alert" className="text-xs text-danger-action-default">
															{meta.error}
														</p>
													) : null}
												</div>
											);
										}}
									</Field>
									<Button type="submit" size="lg" loading={submitting} className="mt-2 w-full">
										{submitting ? t('loginLoggingInMessage') : t('loginButtonLabel')}
									</Button>
									<Disclaimer />
								</CardContent>
							</Card>
						</form>
					)}
				</Form>
			</main>
			<footer className="py-4 text-center">
				<Text as="span" variant="helper">
					{getCurrentCopyrightNoticeText()}
				</Text>
			</footer>
		</div>
	);
};

export {TasklistLoginPage};
