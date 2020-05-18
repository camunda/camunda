/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';

import {login} from '../login.store';

const Tasklist: React.FC = () => {
  const {handleLogout} = login;

  return (
    <main>
      <h1>Tasklist</h1>
      <button type="button" onClick={handleLogout}>
        logout
      </button>
    </main>
  );
};

export {Tasklist};
