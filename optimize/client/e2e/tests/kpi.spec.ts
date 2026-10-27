/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '../fixtures';
import {countOrders, ORDER_PROCESS} from '../seed/dataset';

test('create a process KPI', async ({page, collection, collectionPage, reportPage}) => {
  await collectionPage.goto(collection);
  await collectionPage.createNew('Process KPI');

  const dialog = page.getByRole('dialog', {name: 'Create process KPI'});
  await dialog.getByRole('combobox', {name: 'Select process KPI'}).click();
  await page.getByRole('option', {name: 'Automation rate'}).click();
  await dialog.getByRole('combobox', {name: 'Select process', exact: true}).click();
  await page.getByRole('option', {name: ORDER_PROCESS.name}).click();
  await dialog.getByRole('button', {name: 'Next step'}).click();

  await dialog.getByRole('button', {name: 'Select...'}).first().click();
  const endEventDialog = page.getByRole('dialog', {name: /Select the end events/});
  await reportPage.selectFlowNode(endEventDialog, 'orderShipped');
  await endEventDialog.getByRole('button', {name: 'Update filter'}).click();

  await dialog.getByRole('button', {name: 'Select...'}).click();
  const timeframeDialog = page.getByRole('dialog', {name: /Select the timeframe/});
  await reportPage.chooseDateRange(timeframeDialog, 'Rolling', {value: '5', unit: 'days'});
  await timeframeDialog.getByRole('button', {name: /Update filter|Add filter/}).click();

  await dialog.getByRole('button', {name: 'Create process KPI'}).click();

  await expect(reportPage.editLink).toBeVisible();
  await expect(reportPage.heading).toHaveText(/Automation rate/);
  // Share of ended instances that reached the selected end event.
  const shipped = countOrders((order) => order.inStock && order.outcome === 'completed');
  const ended = countOrders((order) => order.outcome !== 'running');
  await expect(page.getByRole('main')).toContainText(`${((shipped / ended) * 100).toFixed(2)}%`);
});
