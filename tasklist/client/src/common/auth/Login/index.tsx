/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLocation, useNavigate, type Location} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {Form, Field} from 'react-final-form';
import {FORM_ERROR} from 'final-form';
import {authenticationStore} from 'common/auth/authentication';
import {pages} from 'common/routing';
import {getCurrentCopyrightNoticeText} from './getCurrentCopyrightNoticeText';
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
import {CamundaLogo} from 'common/components/CamundaLogo';
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
  const {t} = useTranslation();
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
                  <CamundaLogo aria-label={t('loginLogoLabel')} />
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
                        labelText={t('loginUsernameFieldLabel')}
                        invalid={meta.error && meta.touched}
                        invalidText={meta.error}
                        placeholder={t('loginUsernameFieldPlaceholder')}
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
                  {submitting
                    ? t('loginLoggingInMessage')
                    : t('loginButtonLabel')}
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
