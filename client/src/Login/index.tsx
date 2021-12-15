/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {useHistory} from 'react-router-dom';
import {Form, Field} from 'react-final-form';
import {FORM_ERROR} from 'final-form';

import {login} from 'modules/stores/login';
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

interface FormValues {
  username: string;
  password: string;
}

const Login: React.FC = () => {
  const history = useHistory();
  const {handleLogin} = login;

  return (
    <Container>
      <Form<FormValues>
        onSubmit={async ({username, password}) => {
          try {
            const response = await handleLogin(username, password);
            const referrer = history.location.state?.referrer;

            if (response.ok) {
              return history.push(
                referrer?.pathname === undefined
                  ? {...history.location, pathname: Pages.Initial()}
                  : referrer,
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
