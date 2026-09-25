/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '../fixtures';
import {TemplateDialog} from '../pages/components/TemplateDialog';
import {countOrders, INCIDENT_PROCESS, ORDER_PROCESS} from '../seed/dataset';

test('create, rename, edit and delete a report', async ({
  page,
  collection,
  collectionPage,
  reportPage,
  uniqueName,
}) => {
  const reportName = uniqueName('Order count');

  await collectionPage.goto(collection);
  await collectionPage.createBlankReport();
  await reportPage.enableAutoPreview();
  await reportPage.addDataSources(ORDER_PROCESS.name);
  await reportPage.selectView('Process instance', 'Count');
  await expect(reportPage.number).toHaveText(String(countOrders()));
  await reportPage.rename(reportName);
  await reportPage.save();

  await page.reload();
  await expect(reportPage.heading).toHaveText(reportName);
  await expect(reportPage.number).toHaveText(String(countOrders()));

  await test.step('cancelling an edit discards the changes', async () => {
    await reportPage.edit();
    await reportPage.rename('Discarded name');
    await reportPage.cancel();
    await expect(reportPage.heading).toHaveText(reportName);
  });

  await test.step('deleting the report removes it from the collection', async () => {
    await reportPage.delete();
    await expect(collectionPage.heading).toHaveText(collection.name);
    await expect(collectionPage.list.link(reportName)).toBeHidden();
  });
});

test('create a report from a template', async ({page, collection, collectionPage, reportPage}) => {
  await collectionPage.goto(collection);
  await collectionPage.createNew('Report');
  const dialog = new TemplateDialog(page, 'Create new report');
  await dialog.selectProcess(ORDER_PROCESS.name);
  await dialog.selectTemplate('Analyze shares as pie chart');
  await dialog.confirm();

  await expect(reportPage.nameInput).toHaveValue('Analyze shares as pie chart');
  await expect(reportPage.chart).toBeVisible();
});

test.describe('report setup', () => {
  test.beforeEach(async ({api, collection, reportPage, uniqueName}) => {
    const reportId = await api.createReport(uniqueName('Report setup'), collection.id);
    await reportPage.gotoEdit(collection, reportId);
  });

  test('switch between visualizations', async ({reportPage}) => {
    await reportPage.selectGroupBy('Start date', 'Automatic');

    await reportPage.selectVisualization('Table');
    await expect(reportPage.resultTable).toBeVisible();

    for (const chart of ['Bar chart', 'Line chart', 'Pie chart']) {
      await reportPage.selectVisualization(chart);
      await expect(reportPage.chart).toBeVisible();
    }
  });

  test('count user tasks by assignee', async ({reportPage}) => {
    await reportPage.selectView('User task', 'Count');
    await reportPage.selectGroupBy('Assignee');
    await reportPage.selectVisualization('Table');

    for (const assignee of ['demo', 'john'] as const) {
      await expect(reportPage.resultTable.getByRole('row', {name: assignee})).toContainText(
        String(countOrders((order) => order.assignee === assignee))
      );
    }
  });

  test('show multiple measures', async ({reportPage}) => {
    await reportPage.addMeasure('Duration');

    await expect(reportPage.number).toHaveCount(2);
    await expect(reportPage.number.first()).toHaveText(String(countOrders()));
  });

  test('compare processes grouped by process', async ({reportPage}) => {
    await reportPage.addDataSources(INCIDENT_PROCESS.name);
    await reportPage.selectGroupBy('Process');
    await reportPage.selectVisualization('Table');

    await expect(reportPage.resultTable.getByRole('row', {name: ORDER_PROCESS.name})).toContainText(
      String(countOrders())
    );
    await expect(
      reportPage.resultTable.getByRole('row', {name: INCIDENT_PROCESS.name})
    ).toContainText(String(INCIDENT_PROCESS.instances.length));
  });
});

test('count incidents', async ({api, collection, reportPage, uniqueName}) => {
  const reportId = await api.createReport(uniqueName('Incidents'), collection.id, {
    processKey: INCIDENT_PROCESS.key,
  });
  await reportPage.gotoEdit(collection, reportId);

  await reportPage.selectView('Incident', 'Count');

  await expect(reportPage.number).toHaveText(String(INCIDENT_PROCESS.instances.length));
});

test.describe('in German', () => {
  test.use({locale: 'de-DE'});

  test('show the collection page translated', async ({page, collection, collectionPage}) => {
    await collectionPage.goto(collection);

    await expect(page.getByRole('tab', {name: 'Dashboards & Berichte'})).toBeVisible();
    await expect(page.getByRole('button', {name: 'Neu erstellen'})).toBeVisible();
  });
});
