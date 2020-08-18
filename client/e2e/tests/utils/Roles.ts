/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Role} from 'testcafe';
import {config} from '../../config';
import {screen} from '@testing-library/testcafe';

const demoUser = Role(
  `${config.endpoint}/login`,
  async (t) => {
    await t
      .typeText(screen.getByPlaceholderText('Username'), 'demo')
      .typeText(screen.getByPlaceholderText('Password'), 'demo')
      .click(screen.getByRole('button', {name: 'Login'}));
  },
  {preserveUrl: true},
);

export {demoUser};
