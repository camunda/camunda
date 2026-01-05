/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '@/fixtures/v1-visual';

test.describe('login page', () => {
  test('empty page', async ({page}) => {
    await page.clock.setFixedTime('2026-02-26');
    await page.goto('/login', {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });

  test('field level error', async ({page}) => {
    await page.clock.setFixedTime('2026-02-26');
    await page.goto('/login', {
      waitUntil: 'networkidle',
    });

    await page.getByRole('button', {name: 'Login'}).click();

    await expect(page).toHaveScreenshot();
  });

  test('form level error', async ({page}) => {
    await page.clock.setFixedTime('2026-02-26');
    await page.goto('/login', {
      waitUntil: 'networkidle',
    });
    await page.route('/login', (route) =>
      route.fulfill({
        status: 401,
        headers: {
          'Content-Type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
          message: '',
        }),
      }),
    );

    await page.getByLabel(/^username$/i).fill('demo');
    await page.getByLabel(/^password$/i).fill('wrongpassword');
    await page.getByRole('button', {name: 'Login'}).click();

    await expect(page).toHaveScreenshot();
  });
});
