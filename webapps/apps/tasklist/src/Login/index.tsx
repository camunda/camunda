/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLocation, useNavigate, Location} from 'react-router-dom';
import {Form, Field} from 'react-final-form';
import {FORM_ERROR} from 'final-form';
import {authenticationStore} from 'modules/stores/authentication';
import {pages} from 'modules/routing';
import {getCurrentCopyrightNoticeText} from 'modules/utils/getCurrentCopyrightNoticeText';
import {Disclaimer} from './Disclaimer';
import {
  TextInput,
  PasswordInput,
  Column,
  Grid,
  Stack,
  InlineNotification,
  Button,
} from '@carbon/react';
import {LoadingSpinner} from './LoadingSpinner';
import {CamundaLogo} from 'modules/components/CamundaLogo';
import {z} from 'zod';
import styles from './styles.module.scss';

const locationStateSchema = z.object({
  referrer: z.object({
    pathname: z.string(),
    search: z.string(),
  }),
});

function stateHasReferrer(state: unknown): state is {referrer: Location} {
  const {success} = locationStateSchema.safeParse(state);

  return success;
}

type FormValues = {
  username: string;
  password: string;
};

const Login: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const {handleLogin} = authenticationStore;

  return (
    <Grid as="main" condensed className={styles.container}>
      <Form<FormValues>
        onSubmit={async ({username, password}) => {
          try {
            const {error} = await handleLogin(username, password);

            if (error === null) {
              return navigate(
                stateHasReferrer(location.state)
                  ? location.state.referrer
                  : {...location, pathname: pages.initial},
                {
                  replace: true,
                },
              );
            }

            if (error.response?.status === 401) {
              return {
                [FORM_ERROR]: 'Username and password do not match',
              };
            }

            return {
              [FORM_ERROR]: 'Credentials could not be verified',
            };
          } catch {
            return {
              [FORM_ERROR]: 'Credentials could not be verified',
            };
          }
        }}
        validate={({username, password}) => {
          const errors: {username?: string; password?: string} = {};

          if (!username) {
            errors.username = 'Username is required';
          }

          if (!password) {
            errors.password = 'Password is required';
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
                <div className={styles.logo}>
                  <CamundaLogo aria-label="Camunda logo" />
                </div>
                <h1 className={styles.title}>Tasklist</h1>
              </Stack>
              <Stack gap={3}>
                <span className={styles.error}>
                  {submitError && (
                    <InlineNotification
                      title={submitError}
                      hideCloseButton
                      kind="error"
                      role="alert"
                    />
                  )}
                </span>
                <div className={styles.field}>
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
                <div className={styles.field}>
                  <Field<FormValues['password']>
                    name="password"
                    type="password"
                  >
                    {({input, meta}) => (
                      <PasswordInput
                        {...input}
                        name={input.name}
                        id={input.name}
                        onChange={input.onChange}
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
                  className={styles.button}
                >
                  {submitting ? 'Logging in' : 'Login'}
                </Button>
                <Disclaimer />
              </Stack>
            </Column>
          );
        }}
      </Form>
      <Column
        sm={4}
        md={8}
        lg={16}
        as="span"
        className={styles.copyrightNotice}
      >
        {getCurrentCopyrightNoticeText()}
      </Column>
    </Grid>
  );
};

Login.displayName = 'Login';

export {Login as Component};
