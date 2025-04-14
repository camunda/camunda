/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {
  navigateToApp,
  assertElementVisibilityByName,
} from '@pages/UtilitiesPage';

test.beforeEach(async ({page, taskListLoginPage}) => {
  await navigateToApp(page, 'tasklist');
  await taskListLoginPage.login('demo', 'demo');
  await expect(page).toHaveURL('/tasklist');
});

test.describe('settings', () => {
  // eslint-disable-next-line playwright/expect-expect
  test('change language', async ({page, tasklistHeader}) => {
    await tasklistHeader.changeLanguage('Français');
    await assertElementVisibilityByName(
      page,
      'heading',
      'Bienvenue dans Tasklist',
    );
    await assertElementVisibilityByName(page, 'heading', 'Tâches ouvertes');
    await assertElementVisibilityByName(page, 'button', 'Déconnexion');
  });
});
