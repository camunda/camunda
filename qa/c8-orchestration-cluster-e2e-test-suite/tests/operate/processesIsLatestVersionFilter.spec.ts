/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {randomUUID} from 'node:crypto';
import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {
  seedUniqueProcessDefinitions,
  waitForLatestVersionTotalItems,
} from '@requestHelpers';

/**
 * Regression tests for the ES/OS bug where `/process-definitions/search` with
 * `isLatestVersion: true` returned `page.totalItems` equal to the server page
 * size (~100) instead of the actual count of unique latest-version definitions.
 *
 * The bug caused the Process Name combobox in Operate to truncate at the first
 * ~100 definitions, hiding any beyond that.  N=120 comfortably exceeds the
 * server default.
 */

const SEED_COUNT = 120;
// The regression is triggered purely by `isLatestVersion: true` with more than
// the server page size (~100) of unique definitions, regardless of how many
// versions each has — so we deploy a single version per definition.  Operate's
// dropdown shows the BPMN `name` attribute of the latest version, so adding a
// v2 deployment would change the option label (e.g. `${pid}-v2`) and break the
// id-based lookup below.
const REDEPLOY_FOR_V2_COUNT = 0;

let suffix: string;
let seededIds: string[];

test.beforeAll(async ({request}) => {
  suffix = randomUUID().slice(0, 8);
  seededIds = await seedUniqueProcessDefinitions(
    suffix,
    SEED_COUNT,
    REDEPLOY_FOR_V2_COUNT,
  );
  // Wait until the search index reflects all seeded definitions so that the UI
  // combobox can load them before the tests start.
  await waitForLatestVersionTotalItems(
    request,
    '/process-definitions/search',
    {
      isLatestVersion: true,
      processDefinitionId: {$like: `pd-isLatest-${suffix}-*`},
    },
    SEED_COUNT,
  );
});

test.describe('Operate — Processes isLatestVersion filter', () => {
  test.beforeEach(async ({page, operateHomePage}) => {
    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Process Name dropdown shows all unique latest-version definitions including beyond first page', async ({
    page,
    operateFiltersPanelPage,
  }) => {
    // Regression: before the fix the dropdown truncated at ~100 entries because
    // the API returned totalItems equal to the server page size instead of the
    // real count.  We verify both within the first 100 (indices 0, 49, 99) and
    // beyond (index 119) to confirm the full list is available.
    await expect(operateFiltersPanelPage.processNameFilter).toBeVisible();
    const indicesToCheck = [0, 49, 99, SEED_COUNT - 1];

    for (const idx of indicesToCheck) {
      const id = seededIds[idx]!;
      // Open the combobox, type the full ID to filter to exactly one match
      // (avoids Carbon ComboBox virtualisation hiding offscreen options), then
      // close before the next iteration.
      await operateFiltersPanelPage.processNameFilter.click();
      await operateFiltersPanelPage.processNameFilter.fill(id);
      await expect(
        operateFiltersPanelPage.getOptionByName(id, true),
      ).toBeVisible({timeout: 10_000});
      await page.keyboard.press('Escape');
    }
  });
});
