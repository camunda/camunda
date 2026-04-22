/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {relativizePath, Paths} from 'utils/relativizePath';
import {navigateToApp} from '@pages/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {
  LOGIN_CREDENTIALS,
  createUniqueGlobalTaskListener,
  createEditedGlobalTaskListener,
} from 'utils/constants';
import {waitForItemInList} from 'utils/waitForItemInList';
import {cleanupGlobalTaskListeners} from 'utils/globalTaskListenerCleanup';

const createdListenerIds: string[] = [];

test.describe.serial('global task listeners CRUD', () => {
  let NEW_LISTENER: ReturnType<typeof createUniqueGlobalTaskListener>;
  let EDITED_LISTENER: ReturnType<typeof createEditedGlobalTaskListener>;

  test.beforeAll(() => {
    NEW_LISTENER = createUniqueGlobalTaskListener();
    EDITED_LISTENER = createEditedGlobalTaskListener();
    createdListenerIds.push(NEW_LISTENER.id);
  });

  test.beforeEach(async ({page, loginPage, identityHeader}) => {
    await navigateToApp(page, 'admin');
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(page).toHaveURL(relativizePath(Paths.users()));
    await identityHeader.navigateToGlobalTaskListeners();
    await expect(page).toHaveURL(relativizePath(Paths.globalTaskListeners()));
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test.afterAll(async ({request}) => {
    await cleanupGlobalTaskListeners(request, createdListenerIds);
  });

  test('tries to create a global task listener with invalid type', async ({
    identityGlobalTaskListenersPage,
  }) => {
    await identityGlobalTaskListenersPage.createGlobalTaskListenerButton.click();
    await identityGlobalTaskListenersPage.createGlobalTaskListenerTypeField.fill(
      'invalid type with spaces',
    );
    await expect(
      identityGlobalTaskListenersPage.createGlobalTaskListenerModal,
    ).toContainText(
      'Please enter a valid listener type (letters, digits, dots, dashes, and underscores only)',
    );
    await expect(
      identityGlobalTaskListenersPage.createGlobalTaskListenerTypeField,
    ).toHaveAttribute('data-invalid', 'true');
  });

  test('creates a global task listener', async ({
    page,
    identityGlobalTaskListenersPage,
  }) => {
    await identityGlobalTaskListenersPage.createGlobalTaskListener(
      NEW_LISTENER.id,
      NEW_LISTENER.type,
      NEW_LISTENER.eventType,
    );

    const item = identityGlobalTaskListenersPage.globalTaskListenerCell(
      NEW_LISTENER.id,
    );

    await waitForItemInList(page, item, {
      clickNext: true,
      timeout: 30000,
    });
    await expect(item).toBeVisible();
  });

  test('rejects creating a duplicate global task listener', async ({
    page,
    identityGlobalTaskListenersPage,
  }) => {
    await identityGlobalTaskListenersPage.createGlobalTaskListenerButton.click();
    await expect(
      identityGlobalTaskListenersPage.createGlobalTaskListenerModal,
    ).toBeVisible();

    await identityGlobalTaskListenersPage.createGlobalTaskListenerIdField.fill(
      NEW_LISTENER.id,
    );
    await identityGlobalTaskListenersPage.createGlobalTaskListenerTypeField.fill(
      NEW_LISTENER.type,
    );
    await identityGlobalTaskListenersPage.createGlobalTaskListenerTypeField.blur();
    const createEventTypeToggle =
      identityGlobalTaskListenersPage.createGlobalTaskListenerModal.locator(
        '#event-type-multiselect button',
      );
    await createEventTypeToggle.click();
    const createEventTypeMenu =
      identityGlobalTaskListenersPage.createGlobalTaskListenerModal.locator(
        '#event-type-multiselect .cds--list-box__menu',
      );
    await expect(createEventTypeMenu).toBeVisible();
    await page
      .locator('[role="option"]', {hasText: NEW_LISTENER.eventType})
      .click();
    await identityGlobalTaskListenersPage.createGlobalTaskListenerModalCreateButton.click();

    await expect(
      identityGlobalTaskListenersPage.createGlobalTaskListenerModal,
    ).toBeVisible();
    await expect(
      identityGlobalTaskListenersPage.createGlobalTaskListenerModal.locator(
        '.cds--inline-notification--error',
      ),
    ).toBeVisible();

    await identityGlobalTaskListenersPage.createGlobalTaskListenerModalCancelButton.click();
    await expect(
      identityGlobalTaskListenersPage.createGlobalTaskListenerModal,
    ).toBeHidden();
  });

  test('edits a global task listener', async ({
    page,
    identityGlobalTaskListenersPage,
  }) => {
    const item = identityGlobalTaskListenersPage.globalTaskListenerCell(
      NEW_LISTENER.id,
    );
    await waitForItemInList(page, item, {
      clickNext: true,
      timeout: 30000,
    });
    await expect(item).toBeVisible();

    await identityGlobalTaskListenersPage.editGlobalTaskListener(
      NEW_LISTENER.id,
      EDITED_LISTENER.type,
      EDITED_LISTENER.eventType,
    );

    const updatedTypeCell =
      identityGlobalTaskListenersPage.globalTaskListenerCell(
        EDITED_LISTENER.type,
      );

    await waitForItemInList(page, updatedTypeCell, {
      clickNext: true,
      timeout: 30000,
    });
  });

  test('deletes a global task listener', async ({
    page,
    identityGlobalTaskListenersPage,
  }) => {
    const item = identityGlobalTaskListenersPage.globalTaskListenerCell(
      NEW_LISTENER.id,
    );
    await waitForItemInList(page, item, {
      clickNext: true,
      timeout: 30000,
    });
    await expect(item).toBeVisible();

    await identityGlobalTaskListenersPage.deleteGlobalTaskListener(
      NEW_LISTENER.id,
    );

    await waitForItemInList(page, item, {
      shouldBeVisible: false,
      timeout: 60000,
      clickNext: true,
      emptyStateLocator: identityGlobalTaskListenersPage.emptyStateLocator,
      onAfterReload: async () => {
        await page.goto(relativizePath(Paths.globalTaskListeners()));
        await Promise.race([
          identityGlobalTaskListenersPage.globalTaskListenersList.waitFor({
            timeout: 15000,
          }),
          identityGlobalTaskListenersPage.emptyStateLocator.waitFor({
            timeout: 15000,
          }),
        ]);
      },
    });
  });
});
