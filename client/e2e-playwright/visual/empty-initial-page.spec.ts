/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test, expect} from '@playwright/test';

test.describe('empty initial page', () => {
  test('empty page', async ({page}) => {
    await page.route('**/graphql', (route) => {
      const {operationName} = route.request().postDataJSON();

      switch (operationName) {
        case 'GetCurrentUser':
          return route.fulfill({
            status: 200,
            body: JSON.stringify({
              data: {
                currentUser: {
                  userId: 'demo',
                  displayName: 'demo',
                  permissions: ['READ', 'WRITE'],
                  salesPlanType: null,
                  roles: null,
                  c8Links: [],
                  __typename: 'User',
                },
              },
            }),
          });
        case 'GetTasks':
          return route.fulfill({
            status: 200,
            body: JSON.stringify({
              data: {
                tasks: [],
              },
            }),
          });
        default:
          return route.fulfill({
            status: 500,
            body: JSON.stringify({
              message: '',
            }),
          });
      }
    });

    await page.goto('/');

    await expect(page.locator('body')).toHaveScreenshot();
  });
});
