/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import * as React from 'react';

import {Header} from './Header';

const Tasklist: React.FC = () => {
  return (
    <>
      <Header />
      <main>
        <h1>Tasklist</h1>
      </main>
    </>
  );
};

export {Tasklist};
