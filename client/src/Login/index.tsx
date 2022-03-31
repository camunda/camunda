/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useLocation, useNavigate, Location} from 'react-router-dom';
import {Form, Field} from 'react-final-form';
import {FORM_ERROR} from 'final-form';
import {authenticationStore} from 'modules/stores/authentication';
import {Pages} from 'modules/constants/pages';
import {
  Container,
  FormContainer,
  CopyrightNotice,
  Logo,
  Title,
  Button,
  Error,
  LoadingOverlay,
} from './styled';
import {Input} from './Input';
import {getCurrentCopyrightNoticeText} from 'modules/utils/getCurrentCopyrightNoticeText';
import {Disclaimer} from './Disclaimer';

function stateHasReferrer(state: unknown): state is {referrer: Location} {
  if (typeof state === 'object' && state?.hasOwnProperty('referrer')) {
    return true;
  }

  return false;
}

interface FormValues {
  username: string;
  password: string;
}

const Login: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const {handleLogin} = authenticationStore;

  return (
    <Container>
      <Form<FormValues>
        onSubmit={async ({username, password}) => {
          try {
            const response = await handleLogin(username, password);

            if (response.ok) {
              return navigate(
                stateHasReferrer(location.state)
                  ? location.state.referrer
                  : {...location, pathname: Pages.Initial()},
                {
                  replace: true,
                },
              );
            }

            if (response.status === 401) {
              return {
                [FORM_ERROR]: 'Username and Password do not match',
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
      >
        {({handleSubmit, form, submitError, dirtyFields}) => {
          const {submitting} = form.getState();

          return (
            <form onSubmit={handleSubmit}>
              {submitting && (
                <LoadingOverlay data-testid="login-loading-overlay" />
              )}
              <FormContainer>
                <Logo />
                <Title>Tasklist</Title>
                {submitError !== undefined && <Error>{submitError}</Error>}
                <Field<FormValues['username']> name="username" type="text">
                  {({input}) => (
                    <Input
                      {...input}
                      id={input.name}
                      label="Username"
                      required
                    />
                  )}
                </Field>
                <Field<FormValues['password']> name="password" type="password">
                  {({input}) => (
                    <Input
                      {...input}
                      id={input.name}
                      label="Password"
                      required
                    />
                  )}
                </Field>
                <Button
                  type="submit"
                  disabled={
                    !form
                      .getRegisteredFields()
                      .every((field) => dirtyFields[field]) || submitting
                  }
                >
                  Login
                </Button>
              </FormContainer>
            </form>
          );
        }}
      </Form>
      <Disclaimer />
      <CopyrightNotice>{getCurrentCopyrightNoticeText()}</CopyrightNotice>
    </Container>
  );
};

export {Login};
