/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '../fixtures';
import {confirmDialog} from '../pages/components/dialog';
import {setToggle} from '../pages/components/toggle';

test('search, copy, navigate and bulk delete entities in a collection', async ({
  page,
  api,
  collection,
  collectionPage,
  reportPage,
  deleteAfterTest,
  uniqueName,
}) => {
  const reportName = uniqueName('Order count');
  const dashboardName = uniqueName('Orders board');
  const targetName = uniqueName('Target collection');
  await api.createReport(reportName, collection.id);
  await api.createDashboard(dashboardName, collection.id);
  const targetId = await api.createCollection(targetName);
  deleteAfterTest.collection(`/#/collection/${targetId}/`);

  await collectionPage.goto(collection);

  await test.step('search by name', async () => {
    await collectionPage.list.searchBox.fill(dashboardName);
    await expect(collectionPage.list.rows).toHaveCount(1);
    await expect(collectionPage.list.link(dashboardName)).toBeVisible();
    await collectionPage.list.searchBox.clear();
    await expect(collectionPage.list.rows).toHaveCount(2);
  });

  await test.step('navigate to a report and back via the breadcrumb', async () => {
    await collectionPage.list.open(reportName);
    await expect(reportPage.heading).toHaveText(reportName);
    await page
      .getByRole('navigation', {name: 'Breadcrumb'})
      .getByRole('link', {name: collection.name})
      .click();
    await expect(collectionPage.heading).toHaveText(collection.name);
  });

  await test.step('copy a report into another collection', async () => {
    await collectionPage.list.rowAction(reportName, 'Copy');
    const dialog = page.getByRole('dialog', {name: `Copy ${reportName}`});
    await setToggle(dialog.getByRole('switch', {name: /Move copy to/}), true);
    await dialog.getByRole('combobox', {name: 'Choose an item'}).click();
    await page.getByRole('option', {name: targetName}).click();
    await dialog.getByRole('button', {name: 'Copy', exact: true}).click();

    await expect(collectionPage.heading).toHaveText(targetName);
    await expect(collectionPage.list.link(`${reportName} (copy)`)).toBeVisible();
  });

  await test.step('bulk delete all entities', async () => {
    await collectionPage.goto(collection);
    await expect(collectionPage.list.rows).toHaveCount(2);
    await collectionPage.list.selectAll();
    await collectionPage.list.bulkAction('Delete');
    await confirmDialog(page, /Delete/, /Delete/);
    await expect(collectionPage.list.link(reportName)).toBeHidden();
    await expect(collectionPage.list.link(dashboardName)).toBeHidden();
  });
});
