/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as e from './ProcessReport.elements.js';
import * as Homepage from './Homepage.elements.js';

fixture('Process Report')
  .page(config.endpoint)
  .before(setup)
  .beforeEach(u.login);

test('create and name a report', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt');
  await u.selectView(t, 'Raw Data');

  await t.typeText(e.nameEditField, 'New Name', {replace: true});

  await u.save(t);

  await t.expect(e.reportName.textContent).eql('New Name');
  await t.expect(e.reportRenderer.textContent).contains('invoice');
  await t.expect(e.reportRenderer.textContent).contains('Start Date');
});

test('version selection', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(e.definitionSelection);
  await t.click(e.versionPopover);
  await t.click(e.versionAll);

  const allNumber = +(await e.reportNumber.textContent);

  await t.click(e.versionLatest);

  const latestNumber = +(await e.reportNumber.textContent);

  await t.click(e.versionSpecific);
  await t.click(e.firstVersion);

  const rangeNumber = +(await e.reportNumber.textContent);

  await t.expect(allNumber > rangeNumber).ok();
  await t.expect(rangeNumber > latestNumber).ok();
});

test('sort table columns', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await t.click(e.tableHeader(4));

  let a, b, c;

  a = await e.tableCell(0, 4);
  b = await e.tableCell(1, 4);
  c = await e.tableCell(2, 4);

  await t.expect(a <= b).ok();
  await t.expect(b <= c).ok();

  await t.click(e.tableHeader(4));

  a = await e.tableCell(0, 4);
  b = await e.tableCell(1, 4);
  c = await e.tableCell(2, 4);

  await t.expect(a >= b).ok();
  await t.expect(b >= c).ok();
});

test('exclude raw data columns', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await t.click(e.configurationButton);

  await t.click(e.columnSwitch('Start Date'));

  await t.expect(e.reportRenderer.textContent).notContains('Start Date');
});

test('cancel changes', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);
  await t.typeText(e.nameEditField, 'Another new Name', {replace: true});

  await u.cancel(t);

  await t.expect(e.reportName.textContent).eql('New Name');
});

test('sharing', async t => {
  await t.click(e.report);

  await t.expect(e.shareButton.hasAttribute('disabled')).notOk();

  await t.click(e.shareButton);
  await t.click(e.shareSwitch);

  const shareUrl = await e.shareUrl.value;

  await t.navigateTo(shareUrl);

  await t.expect(e.reportRenderer.visible).ok();
  await t.expect(e.reportRenderer.textContent).contains('invoice');
  await t.expect(e.reportRenderer.textContent).contains('Start Date');
});

test('update definition', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectDefinition(t, 'Lead Qualification');

  await t.expect(e.reportRenderer.textContent).notContains('invoice');
  await t.expect(e.reportRenderer.textContent).contains('leadQualification');
});

test('should only enable valid combinations for process instance count grouped by none', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(e.groupbyDropdown);

  await t.expect(e.option('None').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Start Date of Process Instance').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Variable').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Flow Nodes').hasAttribute('disabled')).ok();

  await t.click(e.option('None'));

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Number').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Table').hasAttribute('disabled')).ok();
  await t.expect(e.option('Bar Chart').hasAttribute('disabled')).ok();
  await t.expect(e.option('Heatmap').hasAttribute('disabled')).ok();

  await t.expect(e.reportNumber.visible).ok();

  await u.save(t);
});

test('Limit the precsion in number report', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectView(t, 'Process Instance', 'Duration');
  await u.selectGroupby(t, 'None');

  await t.click(e.configurationButton);
  await t.click(e.limitPrecisionSwitch);

  await t.expect(e.reportNumber.visible).ok();
});

test('Disable absolute and relative values for table reports', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'Start Date of Process Instance', 'Month');

  await u.selectVisualization(t, 'Table');
  await t.click(e.configurationButton);
  await t.click(e.selectLabel('Show Absolute Value'));
  await t.click(e.selectLabel('Show Relative Value'));

  await t.expect(e.reportTable.textContent).contains('Start Date of Process Instance: Month');
  await t.expect(e.reportTable.textContent).notContains('Process Instance: Count');
  await t.expect(e.reportTable.textContent).notContains('Relative Frequency');
});

test('select process instance count grouped by end date', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectDefinition(t, 'Lead Qualification');
  await u.selectView(t, 'Process Instance', 'Count');

  await u.selectGroupby(t, 'End Date of Process Instance', 'Automatic');

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Number').hasAttribute('disabled')).ok();
  await t.expect(e.option('Table').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Bar Chart').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Line Chart').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Pie Chart').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Heatmap').hasAttribute('disabled')).ok();
  await t.click(e.visualizationDropdown);

  await u.selectVisualization(t, 'Table');
  await t.expect(e.reportTable.visible).ok();
});

test('select process instance count grouped by variable', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectView(t, 'Process Instance', 'Count');

  await u.selectGroupby(t, 'Variable', 'amount');

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Number').hasAttribute('disabled')).ok();
  await t.expect(e.option('Table').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Bar Chart').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Line Chart').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Pie Chart').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Heatmap').hasAttribute('disabled')).ok();

  await t.click(e.visualizationDropdown);

  await u.selectVisualization(t, 'Table');
  await t.expect(e.reportTable.textContent).contains('Variable: amount');
});

test('should only enable valid combinations for Flow Node Count', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectView(t, 'Flow Node', 'Count');

  await t.expect(e.groupbyDropdownButton.textContent).contains('Flow Nodes');

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Number').hasAttribute('disabled')).ok();
  await t.expect(e.option('Table').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Bar Chart').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Heatmap').hasAttribute('disabled')).notOk();

  await t.click(e.option('Table'));
  await u.save(t);
});

test('select which flow nodes to show from the configuration', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectDefinition(t, 'Lead Qualification');

  await t.expect(e.nodeTableCell('received').exists).ok();

  await t.click(e.configurationButton);

  await t.click(e.showFlowNodes);

  await t.click(e.flowNode('msLeadReceived'));

  await t.click(e.primaryModalButton);

  await t.expect(e.nodeTableCell('recieved').exists).notOk();

  u.save(t);
});

test('select to show only running or completed nodes', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await t.click(e.configurationButton);
  await t.click(e.flowNodeStatusSelect);
  await t.click(e.option('Running'));

  await t.click(e.flowNodeStatusSelect);
  await t.click(e.option('Completed'));

  await t.expect(e.reportTable.visible).ok();
});

test('bar/line chart configuration', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectVisualization(t, 'Line Chart');
  await t.click(e.configurationButton);
  await t.click(e.pointMarkersSwitch);

  await t.click(e.pointMarkersSwitch);
  await t.click(e.redColor);
  await t.typeText(e.axisInputs('X Axis Label'), 'x axis label');
  await t.typeText(e.axisInputs('Y Axis Label'), 'y axis label');

  await t.click(e.goalSwitch);

  await t.typeText(e.chartGoalInput, '22', {replace: true});
  await t.expect(e.chartGoalInput.hasAttribute('disabled')).notOk();

  await t.expect(e.reportChart.visible).ok();
});

test('different visualizations', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectDefinition(t, 'Lead Qualification');

  await t.expect(e.reportTable.visible).ok();

  await u.selectVisualization(t, 'Bar Chart');

  await t.expect(e.reportTable.exists).notOk();
  await t.expect(e.reportChart.visible).ok();

  await u.selectVisualization(t, 'Heatmap');

  await t.expect(e.reportChart.exists).notOk();
  await t.expect(e.reportDiagram.visible).ok();

  await u.selectView(t, 'Process Instance', 'Duration');
  await u.selectGroupby(t, 'None');

  await t.expect(e.reportDiagram.exists).notOk();
  await t.expect(e.reportNumber.visible).ok();

  await u.save(t);
});

test('aggregators and reset to default', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectDefinition(t, 'Lead Qualification');
  await t.click(e.filterButton);
  await t.click(e.filterOption('Completed Instances Only'));

  const avg = await e.reportNumber.textContent;

  await u.selectAggregation(t, 'Minimum');

  const min = await e.reportNumber.textContent;

  await u.selectAggregation(t, 'Maximum');

  const max = await e.reportNumber.textContent;

  await t.expect(min).notEql(avg);
  await t.expect(avg).notEql(max);

  await t.click(e.configurationButton);
  await t.click(e.resetButton);
  await t.click(e.configurationButton);

  await t.expect(e.reportNumber.textContent).eql(avg);
});

test('progress bar', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await t.click(e.configurationButton);
  await t.click(e.goalSwitch);
  await t.click(e.configurationButton);

  await t.expect(e.reportProgressBar.visible).ok();
});

test('heatmap target values', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectView(t, 'Flow Node', 'Duration');
  await u.selectVisualization(t, 'Heatmap');

  await t.hover(e.flowNode('UserTask_1g1zsp8'));

  await t.expect(e.tooltip.textContent).notContains('target\u00A0duration');

  await t.click(e.targetValueButton);
  await t.typeText(e.targetValueInput('Do Basic Lead Qualification'), '1');
  await t.click(e.primaryModalButton);

  await t.hover(e.flowNode('UserTask_1g1zsp8'));

  await t.expect(e.tooltip.textContent).contains('target\u00A0duration:\u00A01h');

  await t.click(e.targetValueButton);

  await t.hover(e.flowNode('UserTask_1g1zsp8'));

  await t.expect(e.tooltip.textContent).notContains('target\u00A0duration');

  await u.save(t);
});

test('always show tooltips', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await t.expect(e.tooltip.exists).notOk();

  await t.click(e.configurationButton);
  await t.click(e.tooltipSwitch);

  await t.expect(e.tooltip.visible).ok();
});

test('should only enable valid combinations for user task', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);
  await u.selectView(t, 'User Task', 'Count');

  await t.click(e.groupbyDropdown);

  await t.expect(e.option('None').hasAttribute('disabled')).ok();
  await t.expect(e.option('Flow Nodes').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Assignee').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Candidate Group').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Start Date of Process Instance').hasAttribute('disabled')).ok();

  await t.click(e.option('Flow Nodes'));

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Number').hasAttribute('disabled')).ok();
  await t.expect(e.option('Table').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Bar Chart').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Heatmap').hasAttribute('disabled')).notOk();

  await u.selectGroupby(t, 'Assignee');

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Heatmap').hasAttribute('disabled')).ok();

  await t.click(e.option('Table'));

  await t.expect(e.reportTable.visible).ok();

  await u.save(t);
});

test('should be able to distribute candidate group by user task', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectGroupby(t, 'Candidate Group');

  await u.selectVisualization(t, 'Pie Chart');

  await t.click(e.configurationButton);

  await t.click(e.distributedBySelect);

  await t.click(e.configurationOption('User Task'));

  await t.expect(e.visualizationDropdown.textContent).contains('Bar Chart');

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Table').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Bar Chart').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Number').hasAttribute('disabled')).ok();
  await t.expect(e.option('Line Chart').hasAttribute('disabled')).ok();
  await t.expect(e.option('Pie Chart').hasAttribute('disabled')).ok();

  await t.click(e.option('Table'));

  await t.expect(e.reportTable.textContent).contains('Conduct Discovery Call');
  await t.expect(e.reportTable.textContent).contains('Research Lead');
});

test('should be able to select how the time of the user task is calculated', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await t.click(e.configurationButton);
  await u.selectView(t, 'User Task', 'Duration');

  await u.selectVisualization(t, 'Table');

  await t.click(e.configurationButton);

  await t.click(e.userTaskDurationSelect);

  await t.click(e.option('Idle'));
  await t.expect(e.reportTable.visible).ok();

  await t.click(e.userTaskDurationSelect);

  await t.click(e.option('Work'));
  await t.expect(e.reportTable.visible).ok();
});

test('show process instance count', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await t.click(e.configurationButton);
  await t.click(e.instanceCountSwitch);

  await t.expect(e.instanceCount.visible).ok();
  await t.expect(e.instanceCount.textContent).contains('Total Instance Count:');
});

test('process parts', async t => {
  await t.hover(e.report);
  await t.click(Homepage.contextMenu(e.report));
  await t.click(e.editButton);

  await u.selectView(t, 'Process Instance', 'Duration');
  await u.selectGroupby(t, 'None');

  const withoutPart = await e.reportNumber.textContent;

  await t.click(e.processPartButton);
  await t.click(e.modalFlowNode('UserTask_1g1zsp8'));
  await t.click(e.modalFlowNode('UserTask_0abh7j4'));

  await t.click(e.primaryModalButton);

  const withPart = await e.reportNumber.textContent;

  await t.expect(withoutPart).notEql(withPart);
});

test('deleting', async t => {
  await t.click(e.report);

  await t.click(e.deleteButton);
  await t.click(e.modalConfirmbutton);

  await t.expect(e.report.exists).notOk();
});
