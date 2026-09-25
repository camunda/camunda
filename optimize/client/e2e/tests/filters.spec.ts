/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '../fixtures';
import {countOrders, INCIDENT_PROCESS, ORDER_PROCESS, type OrderOutcome} from '../seed/dataset';

// Every filter narrows a "process instance count" report, so the expected numbers come from the dataset.

test.describe('order process', () => {
  test.beforeEach(async ({api, collection, reportPage, uniqueName}) => {
    const reportId = await api.createReport(uniqueName('Filtered orders'), collection.id, {
      processKey: ORDER_PROCESS.key,
    });
    await reportPage.gotoEdit(collection, reportId);
    await expect(reportPage.number).toHaveText(String(countOrders()));
  });

  const instanceStates: {state: string; outcomes: OrderOutcome[]}[] = [
    {state: 'Running', outcomes: ['running']},
    // Optimize treats every ended instance as completed.
    {state: 'Completed', outcomes: ['completed', 'canceled']},
    {state: 'Canceled', outcomes: ['canceled']},
    {state: 'Non-canceled', outcomes: ['running', 'completed']},
  ];

  for (const {state, outcomes} of instanceStates) {
    test(`instance state filter "${state}"`, async ({reportPage}) => {
      const dialog = await reportPage.openFilter('instance', 'Instance state');
      await reportPage.chooseOption(dialog, state);
      await reportPage.applyFilter(dialog);

      await expect(reportPage.number).toHaveText(
        String(countOrders((order) => outcomes.includes(order.outcome)))
      );
    });
  }

  test('variable filter', async ({page, reportPage}) => {
    const dialog = await reportPage.openFilter('instance', 'Variable');
    await dialog.getByRole('combobox', {name: 'Variable name'}).fill('category');
    await page.getByRole('option', {name: 'category', exact: true}).click();
    await reportPage.chooseValue(dialog, 'books');
    await reportPage.applyFilter(dialog);

    await expect(reportPage.number).toHaveText(
      String(countOrders((order) => order.category === 'books'))
    );
  });

  test('flow node execution filter', async ({reportPage}) => {
    const dialog = await reportPage.openFilter('instance', 'Flow node execution');
    await reportPage.selectFlowNode(dialog, 'orderRejected');
    await reportPage.applyFilter(dialog);

    await expect(reportPage.number).toHaveText(String(countOrders((order) => !order.inStock)));
  });

  test('rolling start date filter', async ({reportPage}) => {
    const dialog = await reportPage.openFilter('instance', 'Instance date', 'Start date');
    await reportPage.chooseDateRange(dialog, 'Rolling', {value: '5', unit: 'days'});
    await reportPage.applyFilter(dialog);

    await expect(reportPage.number).toHaveText(String(countOrders()));
  });
});

test.describe('incident process', () => {
  const incidentFilters = [
    {label: 'Open incidents', incident: 'open'},
    {label: 'Resolved incidents', incident: 'resolved'},
  ] as const;

  for (const {label, incident} of incidentFilters) {
    test(`incident filter "${label}"`, async ({api, collection, reportPage, uniqueName}) => {
      const reportId = await api.createReport(uniqueName('Filtered incidents'), collection.id, {
        processKey: INCIDENT_PROCESS.key,
      });
      await reportPage.gotoEdit(collection, reportId);

      const dialog = await reportPage.openFilter('instance', 'Incident');
      await reportPage.chooseOption(dialog, label);
      await reportPage.applyFilter(dialog);

      await expect(reportPage.number).toHaveText(
        String(INCIDENT_PROCESS.instances.filter((i) => i.incident === incident).length)
      );
    });
  }
});
