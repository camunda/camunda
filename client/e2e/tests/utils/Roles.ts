/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Role} from 'testcafe';
import {config} from '../../config';
import {screen} from '@testing-library/testcafe';

const demoUser = Role(
  `${config.endpoint}/login`,
  async (t) => {
    await t
      .typeText(screen.queryByLabelText('Username'), 'demo')
      .typeText(screen.queryByLabelText('Password'), 'demo')
      .click(screen.queryByRole('button', {name: 'Login'}));
  },
  {preserveUrl: true},
);

export {demoUser};
