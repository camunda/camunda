/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Role} from 'testcafe';
import {config} from '../../config';
import {screen} from '@testing-library/testcafe';

const demoUser = Role(
  `${config.endpoint}`,
  async (t) => {
    await t
      .typeText(
        screen.queryByRole('textbox', {
          name: 'Username',
        }),
        'demo'
      )
      .typeText(screen.queryByLabelText('Password'), 'demo')
      .click(screen.queryByRole('button', {name: 'Log in'}));
  },
  {preserveUrl: true}
);

export {demoUser};
