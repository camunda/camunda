/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Common from './Common.elements.js';
import * as Report from './DecisionReport.elements.js';
import * as ProcessReport from './ProcessReport.elements.js';

fixture('Decision Report').page(config.endpoint).beforeEach(u.login).afterEach(cleanEntities);

test('create a dmn js table report', async (t) => {
  await t.resizeWindow(1400, 700);

  await t.click(Common.createNewMenu);
  await t.click(Common.option('Report'));

  await t.hover(Common.submenuOption('Decision Report'));

  await t.takeElementScreenshot(Common.entityList, 'decision-analysis/img/dmn_report_create.png', {
    crop: {left: 1000, bottom: 300},
  });

  await t.click(Common.submenuOption('Decision Report'));

  await u.toggleReportAutoPreviewUpdate(t);

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'Rules');

  await t.click(Report.configurationButton);
  await t.click(Report.gradientBarsSwitch);

  await t.expect(Report.decisionTable.visible).ok();
  await t.expect(Report.decisionTable.textContent).contains('Hits');
  await t.expect(Report.decisionTableCell(1, 2).textContent).eql('"Misc"');

  await t.typeText(Common.nameEditField, 'Decision Table', {replace: true});

  await t
    .resizeWindow(1400, 700)
    .takeElementScreenshot(Report.report, 'decision-analysis/img/dmn_decision_table.png')
    .maximizeWindow();
});

test('create raw data report', async (t) => {
  await u.createNewDecisionReport(t);

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Raw Data');

  await t.expect(ProcessReport.reportTable.textContent).contains('Decision Definition Key');
  await t.expect(ProcessReport.reportTable.textContent).contains('InputVar');
  await t.expect(ProcessReport.reportTable.textContent).contains('OutputVar');

  await t.typeText(Common.nameEditField, 'DMN - Raw Data Report', {replace: true});

  await t
    .resizeWindow(1400, 700)
    .takeElementScreenshot(Report.report, 'decision-analysis/img/dmn_raw_data_report.png')
    .maximizeWindow();
});

test('save the report', async (t) => {
  await u.createNewDecisionReport(t);

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Raw Data');

  await t.typeText(Common.nameEditField, 'new decision report', {replace: true});
  await u.save(t);

  await t.expect(ProcessReport.reportTable.visible).ok();

  await u.gotoOverview(t);

  await t.expect(Common.reportLabel.textContent).contains('Decision');
});

test('create a single number report', async (t) => {
  await u.createNewDecisionReport(t);

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Evaluation Count');

  await t.expect(ProcessReport.reportNumber.visible).ok();

  await t.typeText(Common.nameEditField, 'Progress of Expected Evaluation Count', {replace: true});

  await t.click(ProcessReport.configurationButton);
  await t.click(ProcessReport.goalSwitch);
  await t.typeText(ProcessReport.goalTargetInput, '1000', {replace: true});
  await t.click(ProcessReport.configurationButton);

  await t
    .resizeWindow(1400, 700)
    .takeElementScreenshot(Report.report, 'decision-analysis/img/dmn_progress_bar.png')
    .maximizeWindow();
});

test('create a report grouped by evaluation date', async (t) => {
  await u.createNewDecisionReport(t);

  await u.selectDefinition(t, 'Assign Approver Group');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'Evaluation Date', 'Automatic');

  await t.click(Report.visualizationDropdown);

  await checkVisualizations(t);

  await t.click(Common.option('Table'));

  await t.expect(ProcessReport.reportTable.visible).ok();

  await u.selectVisualization(t, 'Line Chart');

  await t.typeText(Common.nameEditField, 'Decision Evaluations', {replace: true});

  await t
    .resizeWindow(1400, 700)
    .takeElementScreenshot(Report.report, 'decision-analysis/img/dmn_date_chart.png')
    .maximizeWindow();
});

test('create a report grouped by Input variable', async (t) => {
  await u.createNewDecisionReport(t);

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'Input Variable', 'Invoice Amount');

  await t.click(Report.visualizationDropdown);

  await checkVisualizations(t);

  await t.click(Common.option('Line Chart'));

  await t.expect(ProcessReport.reportChart.visible).ok();

  await u.selectGroupby(t, 'Output Variable', 'Classification');
  await u.selectVisualization(t, 'Pie Chart');

  await t.typeText(Common.nameEditField, 'Distribution of Expense Classification', {replace: true});

  await t
    .resizeWindow(1400, 700)
    .takeElementScreenshot(Report.report, 'decision-analysis/img/dmn_pie_chart.png')
    .maximizeWindow();
});

test('filters', async (t) => {
  await u.createNewDecisionReport(t);

  await u.selectDefinition(t, 'Assign Approver Group');
  await u.selectView(t, 'Evaluation Count');
  await u.selectGroupby(t, 'Rules');

  await t.click(Report.sectionToggle('Filters'));

  await t
    .resizeWindow(1400, 700)
    .click(Report.filterButton)
    .hover(Report.filterOption('Output Variable'))
    .takeElementScreenshot(
      Common.controlPanel,
      'decision-analysis/img/report-with-filterlist-open.png'
    )
    .maximizeWindow();
});

test('show raw data and decision table', async (t) => {
  await u.createNewDecisionReport(t);

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Evaluation Count');

  await u.save(t);

  await t.click(ProcessReport.detailsPopoverButton);
  await t.click(ProcessReport.modalButton('View Raw data'));
  await t.expect(ProcessReport.rawDataTable.visible).ok();
  await t.click(ProcessReport.rawDataModalCloseButton);

  await t.click(ProcessReport.detailsPopoverButton);
  await t.click(ProcessReport.modalButton('View Decision Table'));
  await t.expect(Report.modalDecisionTable.visible).ok();
});

async function checkVisualizations(t) {
  await t.expect(Common.option('Number').hasClass('disabled')).ok();
  await t.expect(Common.option('Table').hasClass('disabled')).notOk();
  await t.expect(Common.option('Bar Chart').hasClass('disabled')).notOk();
  await t.expect(Common.option('Line Chart').hasClass('disabled')).notOk();
  await t.expect(Common.option('Pie Chart').hasClass('disabled')).notOk();
}
