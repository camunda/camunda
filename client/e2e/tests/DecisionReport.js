/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Homepage from './Homepage.elements.js';
import * as Report from './DecisionReport.elements.js';

fixture('Decision Report')
  .page(config.endpoint)
  .before(setup)
  .beforeEach(u.login);

test('create a dmn js table report', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Invoice Classification', '2');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'Rules');

  await t.click(Report.configurationButton);
  await t.click(Report.gradientBarsSwitch);

  await t.expect(Report.decisionTable.visible).ok();
  await t.expect(Report.decisionTableCell(1, 5).textContent).eql('2 (33.3%)');
});

test('create a single number report', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Invoice Classification', '2');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'None');

  await t.expect(Report.reportNumber.visible).ok();
});

test('create a report grouped by evaluation date', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Invoice Classification', '2');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'Evaluation Date', 'Month');

  await t.click(Report.visualizationDropdown);

  await checkVisualizations(t);

  await t.click(Report.option('Table'));

  await t.expect(Report.reportTable.visible).ok();
});

test('create a report grouped by Input variable', async t => {
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Decision Report'));

  await u.selectDefinition(t, 'Invoice Classification', '2');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'Input Variable', 'Invoice Amount');

  await t.click(Report.visualizationDropdown);

  await checkVisualizations(t);

  await t.click(Report.option('Line Chart'));

  await t.expect(Report.reportChart.visible).ok();
});

async function checkVisualizations(t) {
  await t.expect(Report.option('Number').hasAttribute('disabled')).ok();
  await t.expect(Report.option('Table').hasAttribute('disabled')).notOk();
  await t.expect(Report.option('Bar Chart').hasAttribute('disabled')).notOk();
  await t.expect(Report.option('Line Chart').hasAttribute('disabled')).notOk();
  await t.expect(Report.option('Pie Chart').hasAttribute('disabled')).notOk();
}
