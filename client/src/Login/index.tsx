/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useLocation, useNavigate, Location} from 'react-router-dom';
import {Form, Field} from 'react-final-form';
import {FORM_ERROR} from 'final-form';
import {authenticationStore} from 'modules/stores/authentication';
import {Pages} from 'modules/constants/pages';
import {
  Container,
  CopyrightNotice,
  Logo,
  Title,
  Error,
  Button,
  LogoContainer,
  FieldContainer,
} from './styled';
import {getCurrentCopyrightNoticeText} from 'modules/utils/getCurrentCopyrightNoticeText';
import {Disclaimer} from './Disclaimer';
import {
  TextInput,
  PasswordInput,
  Column,
  Grid,
  Stack,
  InlineNotification,
} from '@carbon/react';
import {LoadingSpinner} from './LoadingSpinner';

function stateHasReferrer(state: unknown): state is {referrer: Location} {
  if (typeof state === 'object' && state?.hasOwnProperty('referrer')) {
    return true;
  }

  return false;
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
    <Grid as={Container} condensed>
      <Form<FormValues>
        onSubmit={async ({username, password}) => {
          try {
            const {error} = await handleLogin(username, password);

            if (error === null) {
              return navigate(
                stateHasReferrer(location.state)
                  ? location.state.referrer
                  : {...location, pathname: Pages.Initial()},
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
                <LogoContainer>
                  <Logo aria-label="Camunda logo" />
                </LogoContainer>
                <Title>Tasklist</Title>
              </Stack>
              <Stack gap={3}>
                <Error>
                  {submitError && (
                    <InlineNotification
                      title={submitError}
                      hideCloseButton
                      kind="error"
                      role="alert"
                    />
                  )}
                </Error>
                <FieldContainer>
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
                </FieldContainer>
                <FieldContainer>
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
                </FieldContainer>
                <Button
                  type="submit"
                  disabled={submitting}
                  renderIcon={submitting ? LoadingSpinner : undefined}
                >
                  {submitting ? 'Logging in' : 'Login'}
                </Button>
                <Disclaimer />
              </Stack>
            </Column>
          );
        }}
      </Form>
      <Column sm={4} md={8} lg={16} as={CopyrightNotice}>
        {getCurrentCopyrightNoticeText()}
      </Column>
    </Grid>
  );
};

export {Login};
