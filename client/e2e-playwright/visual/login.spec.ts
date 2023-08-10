/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test, expect} from '@playwright/test';
import {Paths} from 'modules/Routes';

test.describe('login page', () => {
  test('empty page', async ({page}) => {
    await page.goto(Paths.login(), {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });
});
