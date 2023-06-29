/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test} from '@playwright/test';

test.beforeEach(async ({context}) => {
  await context.route('**/client-config.js', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/javascript;charset=UTF-8',
      },
      body: 'window.clientConfig = {"isEnterprise":false,"canLogout":true,"isLoginDelegated":false,"contextPath":"","organizationId":null,"clusterId":null,"stage":null,"mixpanelToken":null,"mixpanelAPIHost":null};',
    }),
  );
});
