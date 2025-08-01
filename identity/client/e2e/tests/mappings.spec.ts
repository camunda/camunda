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

const NEW_MAPPING_RULE = {
  mappingRuleId: "testMappingRuleId",
  name: "Test Mapping rule",
  claimName: "testClaimName",
  claimValue: "testValue",
};

const EDITED_MAPPING_RULE = {
  name: "Edited Mapping rule",
  claimName: "editedClaimName",
  claimValue: "editedClaimValue",
};

test.beforeEach(async ({ page, loginPage }) => {
  await loginPage.navigateToLogin();
  await loginPage.login(LOGIN_CREDENTIALS);
  await expect(page).toHaveURL(relativizePath(Paths.mappingRules()));
});

// this is skipped as it is not currently possible to run e2e tests in OIDC mode.
// TODO: renable when https://github.com/camunda/camunda/issues/31750 is implemented
test.describe.skip("mapping rules CRUD", () => {
  test("create a mapping rule", async ({ page, mappingRulesPage }) => {
    await expect(
      mappingRulesPage.mappingRulesList.getByRole("cell", { name: NEW_MAPPING_RULE.name }),
    ).not.toBeVisible();

    await expect(mappingRulesPage.usersNavItem).not.toBeVisible();

    await mappingRulesPage.createMappingRuleButton.click();
    await expect(mappingRulesPage.createMappingRuleModal).toBeVisible();
    await mappingRulesPage.createMappingRuleIdField.fill(NEW_MAPPING_RULE.mappingRuleId);
    await mappingRulesPage.createMappingRuleNameField.fill(NEW_MAPPING_RULE.name);
    await mappingRulesPage.createMappingRuleClaimNameField.fill(NEW_MAPPING_RULE.claimName);
    await mappingRulesPage.createMappingRuleClaimValueField.fill(
      NEW_MAPPING_RULE.claimValue,
    );
    await mappingRulesPage.createMappingRuleModalCreateButton.click();
    await expect(mappingRulesPage.createMappingRuleModal).not.toBeVisible();

    const item = mappingRulesPage.mappingRulesList.getByRole("cell", {
      name: NEW_MAPPING_RULE.name,
    });

    await waitForItemInList(page, item, {
      emptyStateLocator: mappingRulesPage.emptyState,
    });
  });

  test("edit a mapping rule", async ({ page, mappingRulesPage }) => {
    await expect(
      mappingRulesPage.mappingRulesList.getByRole("cell", { name: NEW_MAPPING_RULE.name }),
    ).toBeVisible();

    await mappingRulesPage.editMappingRuleButton(NEW_MAPPING_RULE.name).click();
    await expect(mappingRulesPage.editMappingRuleModal).toBeVisible();
    await mappingRulesPage.editMappingRuleNameField.fill(EDITED_MAPPING_RULE.name);
    await mappingRulesPage.editMappingRuleClaimNameField.fill(EDITED_MAPPING_RULE.claimName);
    await mappingRulesPage.editMappingRuleClaimValueField.fill(
      EDITED_MAPPING_RULE.claimValue,
    );
    await mappingRulesPage.editMappingRuleModalUpdateButton.click();
    await expect(mappingRulesPage.editMappingRuleModal).not.toBeVisible();

    const item = mappingRulesPage.mappingRulesList.getByRole("cell", {
      name: EDITED_MAPPING_RULE.name,
    });

    await waitForItemInList(page, item);
  });

  test("delete a mapping rule", async ({ page, mappingRulesPage }) => {
    await expect(
      mappingRulesPage.mappingRulesList.getByRole("cell", {
        name: EDITED_MAPPING_RULE.name,
      }),
    ).toBeVisible();

    await mappingRulesPage.deleteMappingRuleButton(EDITED_MAPPING_RULE.name).click();
    await expect(mappingRulesPage.deleteMappingRuleModal).toBeVisible();
    await mappingRulesPage.deleteMappingRuleModalDeleteButton.click();
    await expect(mappingRulesPage.deleteMappingRuleModal).not.toBeVisible();

    const item = mappingRulesPage.mappingRulesList.getByRole("cell", {
      name: EDITED_MAPPING_RULE.name,
    });

    await waitForItemInList(page, item, {
      shouldBeVisible: false,
      emptyStateLocator: mappingRulesPage.emptyState,
    });
  });
});
