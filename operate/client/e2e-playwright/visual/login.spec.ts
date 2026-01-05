/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import {Paths} from 'modules/Routes';

test.describe('login page', () => {
  test('empty page', async ({page}) => {
    await page.clock.setFixedTime('2026-02-26');
    await page.goto(Paths.login(), {
      waitUntil: 'networkidle',
    });

    await expect(page).toHaveScreenshot();
  });
});
