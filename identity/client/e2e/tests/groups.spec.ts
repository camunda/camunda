/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { expect } from "@playwright/test";
import { test } from "../text-fixtures";
import { Paths } from "../utils/paths";
import { relativizePath } from "../utils/relativizePaths";
import { LOGIN_CREDENTIALS } from "../utils/constants";
import { waitForItemInList } from "../utils/waitForItemInList";

const NEW_GROUP = {
  groupId: "testgroupid",
  name: "Test Group",
  description: "test description",
};

const EDITED_GROUP = {
  name: "Edited Group",
  description: "edited description",
};

test.beforeEach(async ({ page, loginPage, groupsPage }) => {
  await loginPage.navigateToLogin();
  await loginPage.login(LOGIN_CREDENTIALS);
  await expect(page).toHaveURL(relativizePath(Paths.users()));
  await groupsPage.navigateToGroups();
  await expect(page).toHaveURL(relativizePath(Paths.groups()));
});

test.describe.serial("groups CRUD", () => {
  test("creates a group", async ({ page, groupsPage }) => {
    await expect(
      groupsPage.groupsList.getByRole("cell", { name: NEW_GROUP.name }),
    ).not.toBeVisible();

    await groupsPage.createGroupButton.click();
    await expect(groupsPage.createGroupModal).toBeVisible();
    await groupsPage.createGroupIdField.fill(NEW_GROUP.groupId);
    await groupsPage.createNameField.fill(NEW_GROUP.name);
    await groupsPage.createDescriptionField.fill(NEW_GROUP.description);
    await groupsPage.createGroupModalCreateButton.click();
    await expect(groupsPage.createGroupModal).not.toBeVisible();

    const item = groupsPage.groupsList.getByRole("cell", {
      name: NEW_GROUP.name,
    });

    await waitForItemInList(page, item);
  });

  test("edits a group", async ({ page, groupsPage }) => {
    await expect(
      groupsPage.groupsList.getByRole("cell", { name: NEW_GROUP.name }),
    ).toBeVisible();

    await groupsPage.editGroupButton(NEW_GROUP.name).click();
    await expect(groupsPage.editGroupModal).toBeVisible();
    await groupsPage.editNameField.fill(EDITED_GROUP.name);
    await groupsPage.editDescriptionField.fill(EDITED_GROUP.description);
    await groupsPage.editGroupModalUpdateButton.click();
    await expect(groupsPage.editGroupModal).not.toBeVisible();

    const item = groupsPage.groupsList.getByRole("cell", {
      name: EDITED_GROUP.name,
    });

    await waitForItemInList(page, item);
  });

  test("deletes a group", async ({ page, groupsPage }) => {
    await expect(
      groupsPage.groupsList.getByRole("cell", { name: EDITED_GROUP.name }),
    ).toBeVisible();

    await groupsPage.deleteGroupButton(EDITED_GROUP.name).click();
    await expect(groupsPage.deleteGroupModal).toBeVisible();
    await groupsPage.deleteGroupModalDeleteButton.click();
    await expect(groupsPage.deleteGroupModal).not.toBeVisible();

    const item = groupsPage.groupsList.getByRole("cell", {
      name: EDITED_GROUP.name,
    });

    await waitForItemInList(page, item, { shouldBeVisible: false });
  });
});
