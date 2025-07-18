/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '@/fixtures/v2-visual';

test.describe('v2 start process from form page', () => {
  test('v2 api not supported message', async ({page}) => {
    await page.goto('/new/foo', {
      waitUntil: 'networkidle',
    });

    await expect(
      page.getByRole('heading', {
        name: 'Public forms not supported',
      }),
    ).toBeVisible();

    await expect(
      page.getByText(
        'Public forms are not supported for this version. Please contact your administrator.',
      ),
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
  });
});
