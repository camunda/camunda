/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@playwright/test';

test.beforeEach(async ({context}) => {
  await context.route('**/client-config.js', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/javascript;charset=UTF-8',
      },
      body: 'window.clientConfig = {"isEnterprise":false,"canLogout":true,"isLoginDelegated":false,"contextPath":"","baseName":"","organizationId":null,"clusterId":null,"stage":null,"mixpanelToken":null,"mixpanelAPIHost":null};',
    }),
  );
});
