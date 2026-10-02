/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '../fixtures';

// Baselines come from the Linux CI runner, see the "update-snapshots" input of the e2e workflow.
// Names are fixed (the collection keeps tests isolated) so the rendered text is identical per run.

test('group by variable submenu stays inside the viewport', async ({
  page,
  api,
  collection,
  reportPage,
}) => {
  const reportId = await api.createReport('Visual report', collection.id);
  await reportPage.gotoEdit(collection, reportId);

  await reportPage.openGroupByMenu();
  await page.getByRole('menuitemcheckbox', {name: 'Variable', exact: true}).hover();
  // Hovering a fixed entry keeps the highlighted item identical between runs.
  await page.getByRole('menuitemcheckbox', {name: 'category'}).hover();

  await expect(page).toHaveScreenshot('group-by-variable-menu.png');
});

test('dashboard with report and text tiles', async ({page, api, collection, dashboardPage}) => {
  const reportId = await api.createReport('Visual order count', collection.id);
  const dashboardId = await api.createDashboard('Visual dashboard', collection.id, [
    {type: 'optimize_report', reportId},
    {type: 'text', text: 'Orders overview'},
  ]);
  await dashboardPage.goto(collection, dashboardId);
  await expect(dashboardPage.tileNumber('Visual order count')).toBeVisible();

  await expect(page).toHaveScreenshot('dashboard-tiles.png');
});
