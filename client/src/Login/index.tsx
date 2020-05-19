/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {useHistory} from 'react-router-dom';
import {Form, Field} from 'react-final-form';

import {login} from '../login.store';
import {Pages} from '../pages';

interface FormValues {
  username: string;
  password: string;
}

const Login: React.FC = () => {
  const [hasError, setHasError] = React.useState(false);
  const history = useHistory();
  const {handleLogin} = login;

  return (
    <Form<FormValues>
      onSubmit={async ({username, password}) => {
        setHasError(false);
        try {
          await handleLogin(username, password);
          history.push(Pages.Initial);
        } catch {
          setHasError(true);
        }
      }}
    >
      {({handleSubmit, form}) => {
        const {invalid, submitting, pristine} = form.getState();

        return (
          <form onSubmit={handleSubmit}>
            <label htmlFor="username">
              Username
              <Field<FormValues['username']>
                name="username"
                id="username"
                required
                component="input"
                type="text"
              />
            </label>
            <label htmlFor="password">
              Password
              <Field<FormValues['password']>
                name="password"
                id="password"
                required
                component="input"
                type="password"
              />
            </label>
            <button type="submit" disabled={invalid || submitting || pristine}>
              Login
            </button>
            {hasError && <h1>error</h1>}
          </form>
        );
      }}
    </Form>
  );
};

export {Login};
