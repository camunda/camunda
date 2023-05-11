/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Report from './ProcessReport.elements.js';
import * as Combined from './CombinedReport.elements.js';
import * as Common from './Common.elements.js';

fixture('Combined Report').page(config.endpoint).beforeEach(u.login).afterEach(cleanEntities);

async function createReport(
  t,
  name,
  definition = 'Lead Qualification',
  visualization = 'Line Chart',
  completed
) {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, definition);

  await u.selectView(t, 'Flow Node', 'Count');
  await u.selectGroupby(t, 'Flow Nodes');
  await u.selectVisualization(t, visualization);

  if (completed) {
    await t.click(Report.sectionToggle('Filters'));
    await t.click(Report.filterButton);
    await t.click(Report.filterOption('Instance State'));
    await t.click(Report.modalOption('Completed'));
    await t.click(Common.carbonModalConfirmBtn);
  }

  await t.typeText(Common.nameEditField, name, {replace: true});

  await u.save(t);

  await u.gotoOverview(t);
}

test('combine two single number reports', async (t) => {
  await createReport(t, 'Leads');
  await createReport(t, 'Monthly Sales');
  await createReport(t, 'Invoice Count');
  await createReport(t, 'Incoming leads per day');
  await createReport(t, 'Weekly rejection Rate');
  await createReport(t, 'Weekly Sales');
  await createReport(t, 'Invoice Average');

  await t.resizeWindow(1400, 700);

  await t.click(Common.createNewMenu);
  await t.click(Common.option('Report'));

  await t.hover(Common.submenuOption('Combined Process Report'));

  await t.takeElementScreenshot(Common.entityList, 'img/combined-report-create.png', {
    crop: {left: 1000, bottom: 300},
  });

  await t.click(Common.submenuOption('Combined Process Report'));
  await u.toggleReportAutoPreviewUpdate(t);
  await t.typeText(Common.nameEditField, 'Combined Report', {replace: true});

  await t
    .resizeWindow(1150, 700)
    .takeScreenshot('img/combined-report.png', {fullPage: true})
    .maximizeWindow();

  await t.click(Combined.singleReport('Leads'));
  await t.click(Combined.singleReport('Invoice Average'));

  await t.expect(Combined.chartRenderer.visible).ok();

  await u.save(t);

  await t.expect(Combined.chartRenderer.visible).ok();

  await u.gotoOverview(t);

  await t.expect(Common.reportLabel.textContent).contains('Combined');
});

test('combine two single table reports and reorder them', async (t) => {
  await createReport(t, 'Another Table Report', 'Lead Qualification', 'Table');
  await createReport(t, 'Table Report', 'Lead Qualification', 'Table', true);

  await u.createNewCombinedReport(t);

  await t.click(Combined.singleReport('Table Report'));
  await t.click(Combined.singleReport('Another Table Report'));

  await t.expect(Report.reportTable.visible).ok();

  await t.typeText(Common.nameEditField, 'Combined Table Report', {replace: true});

  await t
    .resizeWindow(1150, 700)
    .takeScreenshot('img/table-report.png', {fullPage: true})
    .maximizeWindow();

  await t.dragToElement(Combined.singleReport('Table Report'), Combined.dragEndIndicator);

  await t.expect(Report.reportTable.visible).ok();
});

test('combine two single chart reports and change their colors', async (t) => {
  await createReport(t, 'Line Report - 1', 'Lead Qualification', 'Line Chart');
  await createReport(t, 'Line Report - 2', 'Lead Qualification', 'Line Chart', true);

  await u.createNewCombinedReport(t);

  await t.click(Combined.singleReport('Line Report - 1'));
  await t.click(Combined.singleReport('Line Report - 2'));

  await t.expect(Report.reportChart.visible).ok();

  await t.typeText(Common.nameEditField, 'Combined Chart Report', {replace: true});

  await t.resizeWindow(1150, 700);

  await t.click(Combined.reportColorPopover('Line Report - 2'));

  await t.takeScreenshot('img/area-chart-report.png', {fullPage: true}).maximizeWindow();

  await t.click(Combined.redColor);

  await t.expect(Report.reportChart.visible).ok();
});

test('open the configuration popover and add a goal line', async (t) => {
  await createReport(t, 'Bar Report - 1', 'Lead Qualification', 'Bar Chart');
  await createReport(t, 'Bar Report - 2', 'Lead Qualification', 'Bar Chart', true);

  await u.createNewCombinedReport(t);

  await t.click(Combined.singleReport('Bar Report - 1'));
  await t.click(Combined.singleReport('Bar Report - 2'));

  await t.typeText(Common.nameEditField, 'Combined Chart Report', {replace: true});

  await t.resizeWindow(1150, 700);

  await t.click(Combined.configurationButton);
  await t.click(Combined.goalSwitch);
  await t.typeText(Combined.goalInput, '300', {replace: true});

  await t.takeScreenshot('img/combined-config.png', {fullPage: true}).maximizeWindow();

  await t.click(Combined.configurationButton);

  await t.expect(Report.reportChart.visible).ok();
});
