/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '../fixtures';
import {enableSharing} from '../pages/components/share';
import {countOrders} from '../seed/dataset';

test('share a report publicly', async ({
  page,
  anonymousPage,
  api,
  collection,
  reportPage,
  uniqueName,
}) => {
  const reportName = uniqueName('Shared report');
  const reportId = await api.createReport(reportName, collection.id);
  await reportPage.goto(`${collection.url}report/${reportId}/`);

  const shareUrl = await enableSharing(page);

  await anonymousPage.goto(shareUrl);
  await expect(anonymousPage.getByRole('heading', {name: reportName})).toBeVisible();
  await expect(anonymousPage.getByTestId('report-number')).toHaveText(String(countOrders()));

  await test.step('header parameters control the shared header', async () => {
    await anonymousPage.goto(`${shareUrl}?header=hidden`);
    await expect(anonymousPage.getByTestId('report-number')).toBeVisible();
    await expect(anonymousPage.getByRole('heading', {name: reportName})).toBeHidden();

    await anonymousPage.goto(`${shareUrl}?header=titleOnly`);
    await expect(anonymousPage.getByRole('heading', {name: reportName})).toBeVisible();
    await expect(anonymousPage.getByRole('link', {name: 'Open in Optimize'})).toBeHidden();

    await anonymousPage.goto(`${shareUrl}?header=linkOnly`);
    await expect(anonymousPage.getByRole('heading', {name: reportName})).toBeHidden();
    await expect(anonymousPage.getByRole('link', {name: 'Open in Optimize'})).toBeVisible();
  });
});

test('share a dashboard publicly', async ({
  page,
  anonymousPage,
  api,
  collection,
  dashboardPage,
  uniqueName,
}) => {
  const reportName = uniqueName('Shared tile');
  const reportId = await api.createReport(reportName, collection.id);
  const dashboardName = uniqueName('Shared dashboard');
  const dashboardId = await api.createDashboard(dashboardName, collection.id, [
    {type: 'optimize_report', reportId},
  ]);
  await dashboardPage.goto(collection, dashboardId);

  const shareUrl = await enableSharing(page);

  await anonymousPage.goto(shareUrl);
  await expect(anonymousPage.getByRole('heading', {name: dashboardName})).toBeVisible();
  await expect(anonymousPage.getByTestId('report-number')).toHaveText(String(countOrders()));
});
