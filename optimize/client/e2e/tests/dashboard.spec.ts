/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '../fixtures';
import {TemplateDialog} from '../pages/components/TemplateDialog';
import {countOrders, ORDER_PROCESS} from '../seed/dataset';

test('build a dashboard from report, text and website tiles', async ({
  page,
  api,
  collection,
  collectionPage,
  dashboardPage,
  uniqueName,
}) => {
  const reportName = uniqueName('Order count');
  await api.createReport(reportName, collection.id);
  const dashboardName = uniqueName('Orders dashboard');

  await collectionPage.goto(collection);
  await collectionPage.createBlankDashboard();
  await dashboardPage.addTile({type: 'Optimize report', reportName});
  await dashboardPage.addTile({type: 'Text', text: 'Orders overview'});
  await dashboardPage.addTile({type: 'External website', url: 'https://example.com/'});
  await dashboardPage.setDescription('All orders at a glance');
  await dashboardPage.rename(dashboardName);
  await dashboardPage.save();

  await page.reload();
  await expect(dashboardPage.heading).toHaveText(dashboardName);
  await expect(dashboardPage.description).toContainText('All orders at a glance');
  await expect(dashboardPage.tileNumber(reportName)).toHaveText(String(countOrders()));
  await expect(dashboardPage.textTiles).toHaveText('Orders overview');
  await expect(dashboardPage.externalTiles).toHaveAttribute('src', 'https://example.com/');

  await test.step('remove a tile', async () => {
    await dashboardPage.edit();
    await dashboardPage.tileAction(reportName, 'Delete');
    await dashboardPage.save();

    await expect(dashboardPage.reportTile(reportName)).toBeHidden();
    await expect(dashboardPage.textTiles).toHaveCount(1);
  });

  await test.step('cancelling an edit discards the changes', async () => {
    await dashboardPage.edit();
    await dashboardPage.tileAction('Orders overview', 'Delete');
    await dashboardPage.cancel();

    await expect(dashboardPage.textTiles).toHaveCount(1);
  });
});

test('create a dashboard from a template', async ({
  page,
  collection,
  collectionPage,
  dashboardPage,
}) => {
  await collectionPage.goto(collection);
  await collectionPage.createNew('Dashboard');
  const dialog = new TemplateDialog(page, 'Create new dashboard');
  await dialog.selectProcess(ORDER_PROCESS.name);
  await dialog.selectTemplate('Process dashboard');
  await dialog.confirm();
  await dashboardPage.save();

  await expect(dashboardPage.heading).toHaveText('Process dashboard');
  await expect(page.getByTestId('report-number').first()).toBeVisible();
});

test('filter a dashboard by instance state', async ({
  api,
  collection,
  dashboardPage,
  uniqueName,
}) => {
  const reportName = uniqueName('Order count');
  const reportId = await api.createReport(reportName, collection.id);
  const dashboardId = await api.createDashboard(uniqueName('Filtered'), collection.id, [
    {type: 'optimize_report', reportId},
  ]);

  await dashboardPage.goto(collection, dashboardId);
  await dashboardPage.edit();
  await dashboardPage.addFilter('Instance state');
  await dashboardPage.save();
  await expect(dashboardPage.tileNumber(reportName)).toHaveText(String(countOrders()));

  await dashboardPage.applyInstanceStateFilter('Running');

  await expect(dashboardPage.tileNumber(reportName)).toHaveText(
    String(countOrders((order) => order.outcome === 'running'))
  );
});
