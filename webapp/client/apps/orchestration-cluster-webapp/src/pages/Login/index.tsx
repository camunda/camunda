/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Form, Field} from 'react-final-form';
import {FORM_ERROR} from 'final-form';
import {TextInput, PasswordInput, Column, Grid, Stack, InlineNotification, Button} from '@carbon/react';
import {LoadingSpinner} from './LoadingSpinner';
import {CamundaLogo} from '#/modules/components/CamundaLogo';
import {getCurrentCopyrightNoticeText} from './getCurrentCopyrightNoticeText';
import {Disclaimer} from './Disclaimer';
import styles from './styles.module.scss';

type FormValues = {
	username: string;
	password: string;
};

const Login: React.FC = () => {
	return (
		<Grid as="main" condensed className={styles['container']!}>
			<Form<FormValues>
				onSubmit={async ({username: _username, password: _password}) => {
					// TODO: call authentication API, navigate on success, return FORM_ERROR on failure
					// https://github.com/camunda/camunda/issues/51318
					return {[FORM_ERROR]: 'Login not yet implemented.'};
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
									{...(submitting ? {renderIcon: LoadingSpinner} : {})}
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

Login.displayName = 'Login';

export {Login as Component};
