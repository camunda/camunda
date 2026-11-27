/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {publicTest as test} from 'fixtures';
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

const eventTypeLabel = (eventType: string) =>
  eventType.charAt(0).toUpperCase() + eventType.slice(1);

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
    await test.step('Open create modal and fill invalid listener type', async () => {
      await identityGlobalTaskListenersPage.createGlobalTaskListenerButton.click();
      await identityGlobalTaskListenersPage.createGlobalTaskListenerTypeField.fill(
        'invalid type with spaces',
      );
      // The modal does not mark fields touched on blur, only on submit.
      await identityGlobalTaskListenersPage.createGlobalTaskListenerModalSaveButton.click();
    });

    await test.step('Verify validation error is shown', async () => {
      await expect(
        identityGlobalTaskListenersPage.createGlobalTaskListenerModal,
      ).toContainText(
        'Listener type must be 256 characters or fewer, using only letters, numbers, and _ ~ @ . + -',
      );
      await expect(
        identityGlobalTaskListenersPage.createGlobalTaskListenerTypeField,
      ).toHaveAttribute('aria-invalid', 'true');
    });
  });

  test('creates a global task listener', async ({
    page,
    identityGlobalTaskListenersPage,
  }) => {
    await test.step('Create the listener', async () => {
      await identityGlobalTaskListenersPage.createGlobalTaskListener(
        NEW_LISTENER.id,
        NEW_LISTENER.type,
        eventTypeLabel(NEW_LISTENER.eventType),
      );
    });

    await test.step('Verify the listener appears in the list', async () => {
      const item = identityGlobalTaskListenersPage.globalTaskListenerCell(
        NEW_LISTENER.id,
      );
      await waitForItemInList(page, item, {
        clickNext: true,
        timeout: 30000,
      });
      await expect(item).toBeVisible();
    });
  });

  test('rejects creating a duplicate global task listener', async ({
    identityGlobalTaskListenersPage,
  }) => {
    await test.step('Open create modal and fill duplicate data', async () => {
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
      await identityGlobalTaskListenersPage.selectEventType(
        identityGlobalTaskListenersPage.createGlobalTaskListenerEventTypesCombobox,
        eventTypeLabel(NEW_LISTENER.eventType),
      );
    });

    await test.step('Submit and verify duplicate error is shown', async () => {
      await identityGlobalTaskListenersPage.createGlobalTaskListenerModalSaveButton.click();
      await expect(
        identityGlobalTaskListenersPage.createGlobalTaskListenerModal,
      ).toBeVisible();
      await expect(
        identityGlobalTaskListenersPage.createGlobalTaskListenerErrorToast,
      ).toBeVisible();
    });

    await test.step('Close the modal', async () => {
      await identityGlobalTaskListenersPage.createGlobalTaskListenerModalCancelButton.click();
      await expect(
        identityGlobalTaskListenersPage.createGlobalTaskListenerModal,
      ).toBeHidden();
    });
  });

  test('edits a global task listener', async ({
    page,
    identityGlobalTaskListenersPage,
  }) => {
    const item = identityGlobalTaskListenersPage.globalTaskListenerCell(
      NEW_LISTENER.id,
    );

    await test.step('Find the listener in the list', async () => {
      await waitForItemInList(page, item, {
        clickNext: true,
        timeout: 30000,
      });
      await expect(item).toBeVisible();
    });

    await test.step('Edit the listener', async () => {
      await identityGlobalTaskListenersPage.editGlobalTaskListener(
        NEW_LISTENER.id,
        EDITED_LISTENER.type,
        eventTypeLabel(EDITED_LISTENER.eventType),
      );
    });

    await test.step('Verify updated type is visible in the list', async () => {
      const updatedTypeCell =
        identityGlobalTaskListenersPage.globalTaskListenerCell(
          EDITED_LISTENER.type,
        );
      await waitForItemInList(page, updatedTypeCell, {
        clickNext: true,
        timeout: 30000,
      });
    });
  });

  test('deletes a global task listener', async ({
    page,
    identityGlobalTaskListenersPage,
  }) => {
    const item = identityGlobalTaskListenersPage.globalTaskListenerCell(
      NEW_LISTENER.id,
    );

    await test.step('Find the listener in the list', async () => {
      await waitForItemInList(page, item, {
        clickNext: true,
        timeout: 30000,
      });
      await expect(item).toBeVisible();
    });

    await test.step('Delete the listener', async () => {
      await identityGlobalTaskListenersPage.deleteGlobalTaskListener(
        NEW_LISTENER.id,
      );
    });

    await test.step('Verify the listener is removed from the list', async () => {
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
});
