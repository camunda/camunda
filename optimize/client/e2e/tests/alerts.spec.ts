/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '../fixtures';
import {confirmDialog} from '../pages/components/dialog';

test('create, edit, copy and delete an alert', async ({
  page,
  api,
  collection,
  collectionPage,
  uniqueName,
}) => {
  const reportName = uniqueName('Order count');
  await api.createReport(reportName, collection.id);
  const alertName = uniqueName('Too many orders');

  await collectionPage.goto(collection);
  await collectionPage.openTab('Alerts');
  await page.getByRole('button', {name: 'Create new alert'}).click();
  let dialog = page.getByRole('dialog', {name: 'Create new alert'});
  await dialog.getByRole('textbox', {name: 'Alert name'}).fill(alertName);
  await dialog.getByRole('combobox', {name: 'When report'}).click();
  await page.getByRole('option', {name: reportName}).click();
  await dialog.getByRole('textbox', {name: 'Value'}).fill('50');
  await dialog.getByRole('textbox', {name: 'Send email to'}).fill('demo@example.com');
  await dialog.getByRole('textbox', {name: 'Send email to'}).press('Enter');
  await dialog.getByRole('button', {name: 'Create alert'}).click();
  await expect(dialog).toBeHidden();
  await expect(collectionPage.list.row(alertName)).toContainText('has a value above 50');

  await test.step('edit the alert', async () => {
    await collectionPage.list.rowAction(alertName, 'Edit');
    dialog = page.getByRole('dialog');
    await dialog.getByRole('textbox', {name: 'Value'}).fill('200');
    await dialog.getByRole('button', {name: 'Apply changes'}).click();
    await expect(dialog).toBeHidden();
    await expect(collectionPage.list.row(alertName)).toContainText('has a value above 200');
  });

  await test.step('copy the alert', async () => {
    await collectionPage.list.rowAction(alertName, 'Copy');
    dialog = page.getByRole('dialog');
    await dialog.getByRole('button', {name: /Copy/}).click();
    await expect(dialog).toBeHidden();
    await expect(collectionPage.list.rows.filter({hasText: alertName})).toHaveCount(2);
  });

  await test.step('delete both alerts', async () => {
    await collectionPage.list.selectAll();
    await collectionPage.list.bulkAction('Delete');
    await confirmDialog(page, /Delete/, /Delete/);
    await expect(collectionPage.list.row(alertName)).toHaveCount(0);
  });
});
