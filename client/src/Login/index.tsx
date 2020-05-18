/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {useHistory} from 'react-router-dom';

import {login} from '../login.store';
import {Pages} from '../pages';

const Login: React.FC = () => {
  const [hasError, setHasError] = React.useState(false);
  const history = useHistory();
  const {handleLogin} = login;

  return (
    <>
      <button
        type="button"
        onClick={async () => {
          try {
            await handleLogin('demo', 'demo');
            history.push(Pages.Initial);
          } catch {
            setHasError(true);
          }
        }}
      >
        Login
      </button>
      {hasError && <h1>error</h1>}
    </>
  );
};

export {Login};
