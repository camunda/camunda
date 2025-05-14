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

const NEW_MAPPING = {
  mappingId: "testMappingId",
  name: "Test Mapping",
  claimName: "testClaimName",
  claimValue: "testValue",
};

const EDITED_MAPPING = {
  name: "Edited Mapping",
  claimName: "editedClaimName",
  claimValue: "editedClaimValue",
};

test.beforeEach(async ({ page, loginPage }) => {
  await loginPage.navigateToLogin();
  await loginPage.login(LOGIN_CREDENTIALS);
  await expect(page).toHaveURL(relativizePath(Paths.mappings()));
});

// this is skipped as it is not currently possible to run e2e tests in OIDC mode.
// TODO: renable when https://github.com/camunda/camunda/issues/31750 is implemented
test.describe.skip("mappings CRUD", () => {
  test("create a mapping", async ({ page, mappingsPage }) => {
    await expect(
      mappingsPage.mappingsList.getByRole("cell", { name: NEW_MAPPING.name }),
    ).not.toBeVisible();

    await expect(mappingsPage.usersNavItem).not.toBeVisible();

    await mappingsPage.createMappingButton.click();
    await expect(mappingsPage.createMappingModal).toBeVisible();
    await mappingsPage.createMappingIdField.fill(NEW_MAPPING.mappingId);
    await mappingsPage.createMappingNameField.fill(NEW_MAPPING.name);
    await mappingsPage.createMappingClaimNameField.fill(NEW_MAPPING.claimName);
    await mappingsPage.createMappingClaimValueField.fill(
      NEW_MAPPING.claimValue,
    );
    await mappingsPage.createMappingModalCreateButton.click();
    await expect(mappingsPage.createMappingModal).not.toBeVisible();

    const item = mappingsPage.mappingsList.getByRole("cell", {
      name: NEW_MAPPING.name,
    });

    await waitForItemInList(page, item, {
      emptyStateLocator: mappingsPage.emptyState,
    });
  });

  test("edit a mapping", async ({ page, mappingsPage }) => {
    await expect(
      mappingsPage.mappingsList.getByRole("cell", { name: NEW_MAPPING.name }),
    ).toBeVisible();

    await mappingsPage.editMappingButton(NEW_MAPPING.name).click();
    await expect(mappingsPage.editMappingModal).toBeVisible();
    await mappingsPage.editMappingNameField.fill(EDITED_MAPPING.name);
    await mappingsPage.editMappingClaimNameField.fill(EDITED_MAPPING.claimName);
    await mappingsPage.editMappingClaimValueField.fill(
      EDITED_MAPPING.claimValue,
    );
    await mappingsPage.editMappingModalUpdateButton.click();
    await expect(mappingsPage.editMappingModal).not.toBeVisible();

    const item = mappingsPage.mappingsList.getByRole("cell", {
      name: EDITED_MAPPING.name,
    });

    await waitForItemInList(page, item);
  });

  test("delete a mapping", async ({ page, mappingsPage }) => {
    await expect(
      mappingsPage.mappingsList.getByRole("cell", {
        name: EDITED_MAPPING.name,
      }),
    ).toBeVisible();

    await mappingsPage.deleteMappingButton(EDITED_MAPPING.name).click();
    await expect(mappingsPage.deleteMappingModal).toBeVisible();
    await mappingsPage.deleteMappingModalDeleteButton.click();
    await expect(mappingsPage.deleteMappingModal).not.toBeVisible();

    const item = mappingsPage.mappingsList.getByRole("cell", {
      name: EDITED_MAPPING.name,
    });

    await waitForItemInList(page, item, {
      shouldBeVisible: false,
      emptyStateLocator: mappingsPage.emptyState,
    });
  });
});
