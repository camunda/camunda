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
import {mockOIDCModeUI} from 'utils/mockOIDCModeUI';

test.describe.serial('mapping rules CRUD', () => {
  let NEW_MAPPING_RULE: NonNullable<
    ReturnType<typeof createTestData>['mappingRule']
  >;
  let EDITED_MAPPING_RULE: NonNullable<
    ReturnType<typeof createTestData>['editedMappingRule']
  >;

  test.beforeAll(() => {
    const testData = createTestData({
      mappingRule: true,
      editedMappingRule: true,
    });
    NEW_MAPPING_RULE = testData.mappingRule!;
    EDITED_MAPPING_RULE = testData.editedMappingRule!;
  });

  test.beforeEach(async ({page, loginPage}) => {
    await navigateToApp(page, 'admin');
    await mockOIDCModeUI(page);
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(page).toHaveURL(relativizePath(Paths.mappingRules()));
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('tries to create a mapping rule with invalid id', async ({
    identityMappingRulesPage,
  }) => {
    await identityMappingRulesPage.createMappingRuleButton.click();
    await identityMappingRulesPage.createMappingRuleIdField.fill('invalid!!%');
    await expect(identityMappingRulesPage.createMappingRuleModal).toContainText(
      'Please enter a valid mapping rule ID',
    );
    await expect(
      identityMappingRulesPage.createMappingRuleIdField,
    ).toHaveAttribute('data-invalid', 'true');
  });

  test('creates a mapping rule', async ({page, identityMappingRulesPage}) => {
    await identityMappingRulesPage.createMappingRule(
      NEW_MAPPING_RULE.id,
      NEW_MAPPING_RULE.name,
      NEW_MAPPING_RULE.claimName,
      NEW_MAPPING_RULE.claimValue,
    );

    const item = identityMappingRulesPage.mappingRuleCell(
      NEW_MAPPING_RULE.name,
    );

    await waitForItemInList(page, item, {
      clickNext: true,
      timeout: 30000,
    });
  });

  test('edits a mapping rule', async ({page, identityMappingRulesPage}) => {
    const mappingRule = identityMappingRulesPage.mappingRuleCell(
      NEW_MAPPING_RULE.name,
    );
    expect(await findLocatorInPaginatedList(page, mappingRule)).toBe(true);
    await expect(mappingRule).toBeVisible();

    await identityMappingRulesPage.editMappingRule(
      NEW_MAPPING_RULE.name,
      EDITED_MAPPING_RULE.name,
      EDITED_MAPPING_RULE.claimName,
      EDITED_MAPPING_RULE.claimValue,
    );

    const item = identityMappingRulesPage.mappingRuleCell(
      EDITED_MAPPING_RULE.name,
    );

    await waitForItemInList(page, item, {timeout: 60000, clickNext: true});
  });

  test('deletes a mapping rule', async ({page, identityMappingRulesPage}) => {
    const mappingRule = identityMappingRulesPage.mappingRuleCell(
      EDITED_MAPPING_RULE.name,
    );
    expect(await findLocatorInPaginatedList(page, mappingRule)).toBe(true);
    await expect(mappingRule).toBeVisible();

    await identityMappingRulesPage.deleteMappingRule(EDITED_MAPPING_RULE.name);

    const item = identityMappingRulesPage.mappingRuleCell(
      EDITED_MAPPING_RULE.name,
    );

    await waitForItemInList(page, item, {
      shouldBeVisible: false,
      timeout: 60000,
      clickNext: true,
      emptyStateLocator: identityMappingRulesPage.emptyStateLocator,
      onAfterReload: async () => {
        await page.goto(relativizePath(Paths.mappingRules()));
        await Promise.race([
          identityMappingRulesPage.mappingRulesList.waitFor({timeout: 15000}),
          identityMappingRulesPage.emptyStateLocator.waitFor({timeout: 15000}),
        ]);
      },
    });
  });
});
