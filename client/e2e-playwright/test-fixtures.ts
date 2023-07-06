/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test as base} from '@playwright/test';
import {Common} from './pages/Common';
import {Login} from './pages/Login';

type Fixture = {
  resetData: () => Promise<void>;
  commonPage: Common;
  loginPage: Login;
};

const test = base.extend<Fixture>({
  commonPage: async ({page}, use) => {
    await use(new Common(page));
  },
  loginPage: async ({page}, use) => {
    await use(new Login(page));
  },
});

export {test};
