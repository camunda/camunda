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
import {waitForItemInList} from 'utils/waitForItemInList';
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
    await navigateToApp(page, 'identity');
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
      emptyStateLocator: identityMappingRulesPage.emptyState,
    });

    await expect(item).toBeVisible();
  });

  test('edits a mapping rule', async ({page, identityMappingRulesPage}) => {
    await expect(
      identityMappingRulesPage.mappingRuleCell(NEW_MAPPING_RULE.name),
    ).toBeVisible();

    await identityMappingRulesPage.editMappingRule(
      NEW_MAPPING_RULE.name,
      EDITED_MAPPING_RULE.name,
      EDITED_MAPPING_RULE.claimName,
      EDITED_MAPPING_RULE.claimValue,
    );

    const item = identityMappingRulesPage.mappingRuleCell(
      EDITED_MAPPING_RULE.name,
    );

    await waitForItemInList(page, item);
  });

  test('deletes a mapping rule', async ({page, identityMappingRulesPage}) => {
    await expect(
      identityMappingRulesPage.mappingRuleCell(EDITED_MAPPING_RULE.name),
    ).toBeVisible();

    await identityMappingRulesPage.deleteMappingRule(EDITED_MAPPING_RULE.name);

    const item = identityMappingRulesPage.mappingRuleCell(
      EDITED_MAPPING_RULE.name,
    );

    await waitForItemInList(page, item, {
      shouldBeVisible: false,
      emptyStateLocator: identityMappingRulesPage.emptyState,
    });
  });
});
