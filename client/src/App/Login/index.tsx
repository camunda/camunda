/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {useHistory} from 'react-router-dom';
import {Form, Field} from 'react-final-form';
import {FORM_ERROR} from 'final-form';
import Button from 'modules/components/Button';
import {PAGE_TITLE} from 'modules/constants';
import {clearStateLocally} from 'modules/utils/localStorage';
import {Disclaimer} from './Disclaimer';
import {LOGIN_ERROR, GENERIC_ERROR} from './constants';
import {
  Container,
  LoginHeader,
  Logo,
  LoginTitle,
  LoginForm,
  FormError,
  Username,
  Password,
  Copyright,
} from './styled';
import {Routes} from 'modules/routes';
import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import {authenticationStore} from 'modules/stores/authentication';
import {NetworkError} from 'modules/networkError';

type FormValues = {
  username: string;
  password: string;
};

type LocationState = {
  referrer?: Location;
};

const Login: React.FC = () => {
  const history = useHistory<LocationState>();

  useEffect(() => {
    document.title = PAGE_TITLE.LOGIN;
  }, []);

  return (
    <Form<FormValues>
      onSubmit={async (values) => {
        const response = await authenticationStore.handleLogin(values);

        if (response === undefined) {
          clearStateLocally();
          history.replace(
            history.location.state?.referrer === undefined
              ? {
                  ...history.location,
                  pathname: Routes.dashboard(),
                }
              : history.location.state.referrer
          );
          return;
        }

        if (response instanceof NetworkError && response.status === 401) {
          return {
            [FORM_ERROR]: LOGIN_ERROR,
          };
        }

        return {
          [FORM_ERROR]: GENERIC_ERROR,
        };
      }}
    >
      {({handleSubmit, submitting, submitError, form, dirtyFields}) => (
        <>
          {submitting && <SpinnerSkeleton data-testid="spinner" />}
          <Container>
            <LoginHeader>
              <Logo />
              <LoginTitle>Operate</LoginTitle>
            </LoginHeader>
            <LoginForm onSubmit={handleSubmit}>
              {submitError && <FormError>{submitError}</FormError>}
              <Field<FormValues['username']> name="username" component="input">
                {({input}) => (
                  <Username
                    name={input.name}
                    id={input.name}
                    value={input.value}
                    onChange={input.onChange}
                    type="text"
                    aria-label="User Name"
                    required
                    label="Username"
                  />
                )}
              </Field>
              <Field<FormValues['password']> name="password" component="input">
                {({input}) => (
                  <Password
                    name={input.name}
                    id={input.name}
                    value={input.value}
                    onChange={input.onChange}
                    type="password"
                    aria-label="Password"
                    required
                    label="Password"
                  />
                )}
              </Field>
              <Button
                type="submit"
                size="large"
                title="Log in"
                disabled={
                  !form
                    .getRegisteredFields()
                    .every((field) => dirtyFields[field]) || submitting
                }
              >
                Log in
              </Button>
            </LoginForm>
            <Disclaimer />
            <Copyright />
          </Container>
        </>
      )}
    </Form>
  );
};

export {Login};
