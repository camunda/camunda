/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, Page} from '@playwright/test';
import {OperateDashboardPage} from '@pages/OperateDashboardPage';
import {OperateProcessInstancePage} from '@pages/OperateProcessInstancePage';

// Incidents whose messages truncate to the same text share one dashboard
// group, so the filtered view can list more than one instance. Open each
// view-instance link until the one carrying the expected variable is found.
export async function findInstanceWithVariable(
  page: Page,
  operateDashboardPage: OperateDashboardPage,
  operateProcessInstancePage: OperateProcessInstancePage,
  expectedVariableRegex: RegExp,
): Promise<boolean> {
  const viewInstanceLinks = operateDashboardPage.viewInstanceLink();
  await expect(viewInstanceLinks.first()).toBeVisible();

  const linkCount = await viewInstanceLinks.count();
  for (let index = 0; index < linkCount; index++) {
    await operateDashboardPage.viewInstanceLink().nth(index).click();

    const variableVisible = await operateProcessInstancePage
      .variableCellByName(expectedVariableRegex)
      .waitFor({state: 'visible', timeout: 10000})
      .then(() => true)
      .catch(() => false);
    if (variableVisible) {
      return true;
    }

    await page.goBack();
    await expect(operateDashboardPage.viewInstanceLink().first()).toBeVisible();
  }

  return false;
}
