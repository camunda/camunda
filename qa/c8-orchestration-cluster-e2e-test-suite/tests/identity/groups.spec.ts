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
import {LOGIN_CREDENTIALS, createTestData} from 'utils/constants';
import {
  findLocatorInPaginatedList,
  waitForItemInList,
} from 'utils/waitForItemInList';
import {cleanupGroupsSafely} from 'utils/groupsCleanup';

test.describe.serial('groups CRUD', () => {
  let NEW_GROUP: NonNullable<ReturnType<typeof createTestData>['group']>;
  let EDITED_GROUP: NonNullable<
    ReturnType<typeof createTestData>['editedGroup']
  >;

  test.beforeAll(() => {
    const testData = createTestData({
      group: true,
      editedGroup: true,
    });
    NEW_GROUP = testData.group!;
    EDITED_GROUP = testData.editedGroup!;
  });

  test.beforeEach(async ({page, loginPage, identityGroupsPage}) => {
    await navigateToApp(page, 'admin');
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(page).toHaveURL(relativizePath(Paths.users()));
    await identityGroupsPage.navigateToGroups();
    await expect(page).toHaveURL(relativizePath(Paths.groups()));
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('tries to create a group with invalid id', async ({
    identityGroupsPage,
  }) => {
    await identityGroupsPage.createGroupButton.click();
    await identityGroupsPage.createGroupIdField.fill('invalid!!%');
    await expect(identityGroupsPage.createGroupModal).toContainText(
      'Please enter a valid group ID',
    );
    await expect(identityGroupsPage.createGroupIdField).toHaveAttribute(
      'data-invalid',
      'true',
    );
  });

  test('creates a group', async ({page, identityGroupsPage}) => {
    await identityGroupsPage.createGroup(
      NEW_GROUP.groupId,
      NEW_GROUP.name,
      NEW_GROUP.description,
    );

    const item = identityGroupsPage.groupCell(NEW_GROUP.name);

    await waitForItemInList(page, item, {
      clickNext: true,
      timeout: 30000,
    });
  });

  test('edits a group', async ({page, identityGroupsPage}) => {
    const group = identityGroupsPage.groupCell(NEW_GROUP.name);
    expect(await findLocatorInPaginatedList(page, group)).toBe(true);
    await expect(group).toBeVisible();

    await identityGroupsPage.editGroup(
      NEW_GROUP.name,
      EDITED_GROUP.name,
      EDITED_GROUP.description,
    );

    const item = identityGroupsPage.groupCell(EDITED_GROUP.name);

    await waitForItemInList(page, item, {timeout: 60000, clickNext: true});
  });

  test('deletes a group', async ({page, identityGroupsPage}) => {
    await identityGroupsPage.deleteGroup(EDITED_GROUP.name);

    const item = identityGroupsPage.groupCell(EDITED_GROUP.name);

    await waitForItemInList(page, item, {
      shouldBeVisible: false,
      timeout: 60000,
      clickNext: true,
      emptyStateLocator: identityGroupsPage.emptyStateLocator,
      onAfterReload: async () => {
        await page.goto(relativizePath(Paths.groups()));
        await Promise.race([
          identityGroupsPage.groupsList.waitFor({timeout: 15000}),
          identityGroupsPage.emptyStateLocator.waitFor({timeout: 20000}),
        ]);
      },
    });
  });
});

test.describe('Groups functionalities', () => {
  const createdGroupIds: string[] = [];

  test.beforeEach(async ({page, loginPage, identityGroupsPage}) => {
    await navigateToApp(page, 'admin');
    await loginPage.login('demo', 'demo');
    await expect(identityGroupsPage.groupsList).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test.afterAll(async ({browser}) => {
    if (createdGroupIds.length > 0) {
      const context = await browser.newContext();
      const page = await context.newPage();

      try {
        await cleanupGroupsSafely(page, createdGroupIds);
      } catch (error) {
        console.warn('Cleanup failed:', error);
      } finally {
        await context.close();
      }
    }
  });

  test('As an Admin user can create a group with particular permissions and assign it to Test user', async ({
    page,
    identityGroupsPage,
    identityAuthorizationsPage,
    identityHeader,
  }) => {
    const testData = createTestData({
      group: true,
    });
    const TEST_GROUP = testData.group!;

    createdGroupIds.push(TEST_GROUP.groupId);

    await test.step('Create test group', async () => {
      await identityGroupsPage.navigateToGroups();
      await identityGroupsPage.createGroup(
        TEST_GROUP.groupId,
        TEST_GROUP.name,
        TEST_GROUP.description,
      );

      const item = identityGroupsPage.groupCell(TEST_GROUP.name);
      await waitForItemInList(page, item, {
        clickNext: true,
        timeout: 60000,
      });
      await expect(item).toBeVisible();
    });

    await test.step('Assign user to group', async () => {
      await identityGroupsPage.clickGroupId(TEST_GROUP.name);
      await identityGroupsPage.assignUserToGroup('lisa', 'lisa@example.com');
      await page.reload();
    });

    await test.step('Create authorization for group', async () => {
      await identityHeader.navigateToAuthorizations();
      await identityAuthorizationsPage.createAuthorization({
        ownerType: 'Group',
        ownerId: TEST_GROUP.name,
        resourceType: 'AUDIT_LOG',
        resourceId: '*',
        accessPermissions: ['Read'],
      });
    });

    await test.step('Verify authorization was created', async () => {
      await identityAuthorizationsPage.clickResourceType('AUDIT_LOG');
      await identityAuthorizationsPage.assertAuthorizationExists(
        TEST_GROUP.groupId,
        'Group',
        ['Read'],
      );
    });
  });
});
