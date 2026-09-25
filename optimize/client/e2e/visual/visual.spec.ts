/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '../fixtures';

// Baselines come from the Linux CI runner, see the "update-snapshots" input of the e2e workflow.

test('group by variable submenu stays inside the viewport', async ({
  page,
  api,
  collection,
  reportPage,
  uniqueName,
}) => {
  const reportId = await api.createReport(uniqueName('Visual'), collection.id);
  await reportPage.gotoEdit(collection, reportId);

  await reportPage.openGroupByMenu();
  await page.getByRole('menuitemcheckbox', {name: 'Variable', exact: true}).hover();
  await expect(page.getByRole('menuitemcheckbox', {name: 'category'})).toBeVisible();

  await expect(page).toHaveScreenshot('group-by-variable-menu.png', {
    mask: [page.getByRole('navigation', {name: 'Breadcrumb'}), reportPage.nameInput],
  });
});

test('dashboard with report and text tiles', async ({
  page,
  api,
  collection,
  dashboardPage,
  uniqueName,
}) => {
  const reportId = await api.createReport('Visual order count', collection.id);
  const dashboardId = await api.createDashboard(uniqueName('Visual'), collection.id, [
    {type: 'optimize_report', reportId},
    {type: 'text', text: 'Orders overview'},
  ]);
  await dashboardPage.goto(collection, dashboardId);
  await expect(dashboardPage.tileNumber('Visual order count')).toBeVisible();

  await expect(page).toHaveScreenshot('dashboard-tiles.png', {
    mask: [page.getByRole('navigation', {name: 'Breadcrumb'}), dashboardPage.heading],
  });
});
