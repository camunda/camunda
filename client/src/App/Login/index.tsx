/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';
import {useHistory} from 'react-router-dom';
import {Form, Field} from 'react-final-form';
import {FORM_ERROR} from 'final-form';

import {login} from 'modules/api/login';
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
  UsernameInput,
  PasswordInput,
  Copyright,
} from './styled';
import SpinnerSkeleton from 'modules/components/SpinnerSkeleton';
import {Routes} from 'modules/routes';

type FormValues = {
  username: string;
  password: string;
};

type LocationState = {
  referrer?: string;
  isLoggedIn?: boolean;
};

function Login() {
  const history = useHistory<LocationState>();

  useEffect(() => {
    document.title = PAGE_TITLE.LOGIN;
  }, []);

  return (
    <Form<FormValues>
      onSubmit={async (values) => {
        try {
          const response = await login(values);

          if (response.status === 401) {
            return {
              [FORM_ERROR]: LOGIN_ERROR,
            };
          }

          if (!response.ok) {
            return {
              [FORM_ERROR]: GENERIC_ERROR,
            };
          }
          clearStateLocally();
          history.push({
            pathname: history.location.state?.referrer ?? Routes.dashboard(),
            search: history.location.search,
            state: {
              isLoggedIn: true,
            },
          });
        } catch {
          return {
            [FORM_ERROR]: GENERIC_ERROR,
          };
        }
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
                  <UsernameInput
                    name={input.name}
                    id={input.name}
                    value={input.value}
                    onChange={input.onChange}
                    type="text"
                    placeholder="Username"
                    aria-label="User Name"
                    required
                  />
                )}
              </Field>
              <Field<FormValues['password']> name="password" component="input">
                {({input}) => (
                  <PasswordInput
                    name={input.name}
                    id={input.name}
                    value={input.value}
                    onChange={input.onChange}
                    type="password"
                    placeholder="Password"
                    aria-label="Password"
                    required
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
}

export {Login};
