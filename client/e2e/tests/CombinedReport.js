/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Homepage from './Homepage.elements.js';
import * as Report from './ProcessReport.elements.js';
import * as Combined from './CombinedReport.elements.js';

fixture('Combined Report')
  .page(config.endpoint)
  .before(setup)
  .beforeEach(u.login);

test('combine two single number reports', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Lead Qualification');

  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');

  await t.typeText(Report.nameEditField, 'Report 1', {replace: true});

  await u.save(t);

  await u.gotoOverview(t);

  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt');

  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');

  await t.typeText(Report.nameEditField, 'Report 2', {replace: true});

  await u.save(t);

  await u.gotoOverview(t);

  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Combined Process Report'));

  await t.click(Combined.singleReport('Report 1'));
  await t.click(Combined.singleReport('Report 2'));

  await t.expect(Combined.chartRenderer.visible).ok();

  await u.save(t);

  await t.expect(Combined.chartRenderer.visible).ok();

  await u.gotoOverview(t);

  await t.expect(Homepage.reportLabel.textContent).contains('Combined');
});

test('combine two single table reports', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Lead Qualification');

  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'Start Date of Process Instance', 'Year');
  await u.selectVisualization(t, 'Table');

  await t.typeText(Report.nameEditField, 'Table Report 1', {replace: true});

  await u.save(t);

  await u.gotoOverview(t);

  await u.createNewReport(t);
  await u.selectDefinition(t, 'Lead Qualification');

  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'End Date of Process Instance', 'Year');
  await u.selectVisualization(t, 'Table');

  await t.typeText(Report.nameEditField, 'Table Report 2', {replace: true});

  await u.save(t);

  await u.gotoOverview(t);

  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Combined Process Report'));

  await t.click(Combined.singleReport('Table Report 1'));
  await t.click(Combined.singleReport('Table Report 2'));

  await t.expect(Combined.reportTable.visible).ok();

  await t.typeText(Report.nameEditField, 'Combined Table Report', {replace: true});

  await u.save(t);
});

test('reorder table reports', async t => {
  const combinedChartReport = Combined.report('Combined Table Report');
  await t.hover(combinedChartReport);
  await t.click(Homepage.contextMenu(combinedChartReport));
  await t.click(Combined.editButton(combinedChartReport));

  await t.dragToElement(Combined.singleReport('Table Report 1'), Combined.dragEndIndicator);

  await t.expect(Combined.reportTable.visible).ok();
});

test('combine two single chart reports', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt');

  await u.selectView(t, 'Flow Node', 'Duration');
  await u.selectVisualization(t, 'Bar Chart');

  await t.typeText(Report.nameEditField, 'Chart Report 1', {replace: true});

  await u.save(t);

  await u.gotoOverview(t);

  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt');

  await u.selectView(t, 'User Task', 'Duration');
  await u.selectGroupby(t, 'Flow Nodes');
  await u.selectVisualization(t, 'Bar Chart');

  await t.typeText(Report.nameEditField, 'Chart Report 2', {replace: true});

  await u.save(t);

  await u.gotoOverview(t);

  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Report'));
  await t.click(Homepage.submenuOption('Combined Process Report'));

  await t.click(Combined.singleReport('Chart Report 1'));
  await t.click(Combined.singleReport('Chart Report 2'));

  await t.expect(Combined.reportChart.visible).ok();

  await t.typeText(Report.nameEditField, 'Combined Chart Report', {replace: true});

  await u.save(t);
});

test('change the color of one of the report in a combined chart report', async t => {
  const combinedChartReport = Combined.report('Combined Chart Report');
  await t.hover(combinedChartReport);
  await t.click(Homepage.contextMenu(combinedChartReport));
  await t.click(Combined.editButton(combinedChartReport));

  await t.click(Combined.reportColorPopover('Chart Report 1'));

  await t.click(Combined.redColor);

  await t.expect(Combined.reportChart.visible).ok();

  await u.save(t);
});

test('open the configuration popover and add a goal line', async t => {
  const combinedChartReport = Combined.report('Combined Chart Report');
  await t.hover(combinedChartReport);
  await t.click(Homepage.contextMenu(combinedChartReport));
  await t.click(Combined.editButton(combinedChartReport));
  await t.click(Combined.configurationButton);
  await t.click(Combined.goalSwitch);
  await t.click(Combined.configurationButton);

  await t.expect(Combined.reportChart.visible).ok();
});
