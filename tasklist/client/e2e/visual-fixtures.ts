/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test as base} from './test-fixtures';
import {type Page} from '@playwright/test';

const MOCK_TENANTS = [
  {
    tenantId: 'tenantA',
    name: 'Tenant A',
  },
  {
    tenantId: 'tenantB',
    name: 'Tenant B',
  },
];

type PlaywrightFixtures = {
  page: Page;
};

const test = base.extend<PlaywrightFixtures>({
  page: async ({page}, use) => {
    await page.route('**/v2/license', (route) =>
      route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          validLicense: false,
          licenseType: 'unknown',
        }),
      }),
    );
    await page.route('**/v2/authentication/me', (route) =>
      route.fulfill({
        status: 200,
        body: JSON.stringify({
          userId: 'demo',
          displayName: 'demo',

          salesPlanType: null,
          roles: null,
          c8Links: [],
          tenants: MOCK_TENANTS,
        }),
        headers: {
          'content-type': 'application/json',
        },
      }),
    );
    await page.route('**/client-config.js', (route) =>
      route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'text/javascript;charset=UTF-8',
        },
        body: `window.clientConfig = {
                isEnterprise: false,
                isMultiTenancyEnabled: false,
                canLogout: true,
                isLoginDelegated: false,
                contextPath: "",
                baseName: "/",
                organizationId: null,
                clusterId: null,
                stage: null,
                mixpanelToken: null,
                mixpanelAPIHost: null,
                isResourcePermissionsEnabled: false,
                isUserAccessRestrictionsEnabled: true,
              };
            `,
      }),
    );

    await use(page);
  },
});

export {test, MOCK_TENANTS};
