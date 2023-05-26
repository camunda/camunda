/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test, expect} from '@playwright/test';
import schema from '../bigForm.json';

test.describe('start process from form page', () => {
  test('initial page', async ({page}) => {
    await page.route(/^.*\/v1.*$/i, (route) => {
      if (route.request().url().includes('v1/external/process/foo/form')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            id: 'foo',
            processDefinitionKey: '2251799813685255',
            schema: JSON.stringify(schema),
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      return route.continue();
    });

    await page.goto('/new/foo', {
      waitUntil: 'networkidle',
    });

    expect(page.getByText('Title 1')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });
});
