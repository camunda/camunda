/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';
import {addAnnotation, clearAllAnnotations} from '../browserMagic';

import * as e from './ProcessReport.elements.js';
import * as Homepage from './Homepage.elements.js';

fixture('Process Report').page(config.endpoint).beforeEach(u.login).afterEach(cleanEntities);

test('create a report from a template', async (t) => {
  await t.resizeWindow(1300, 750);
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.newReportOption);
  await t.click(Homepage.submenuOption('Process Report'));

  await t.click(e.templateModalProcessField);
  await t.click(e.option('Invoice Receipt with alternative correlation variable'));

  await t.click(e.templateOption('Heatmap: Flownode count'));

  await t.takeScreenshot('process/single-report/reportTemplate.png', {fullPage: true});
  await t.maximizeWindow();

  await t.click(e.modalConfirmbutton);

  await t.expect(e.nameEditField.value).eql('Heatmap: Flownode count');
  await t.expect(e.groupbyDropdownButton.textContent).contains('Flow Nodes');
  await t.expect(e.reportDiagram.visible).ok();
});

test('create and name a report', async (t) => {
  await u.createNewReport(t);

  await t.typeText(e.nameEditField, 'Invoice Pipeline', {replace: true});

  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Flow Node', 'Count');

  await t.resizeWindow(1350, 750);

  await u.selectVisualization(t, 'Heatmap');

  await t.takeScreenshot('process/single-report/report-reportEditActions.png', {fullPage: true});
  await t.maximizeWindow();

  await u.save(t);

  await t.expect(e.reportName.textContent).eql('Invoice Pipeline');
});

test('sharing', async (t) => {
  await u.createNewReport(t);

  await t.typeText(e.nameEditField, 'Invoice Pipeline', {replace: true});

  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Flow Node', 'Count');

  await t.resizeWindow(1000, 650);

  await u.selectVisualization(t, 'Heatmap');
  await u.save(t);

  await t.expect(e.shareButton.hasClass('disabled')).notOk();

  await t.click(e.shareButton);
  await t.click(e.shareSwitch);

  await t
    .takeScreenshot('process/single-report/report-sharingPopover.png', {fullPage: true})
    .maximizeWindow();

  const shareUrl = await e.shareUrl.value;

  await t.navigateTo(shareUrl);

  await t.expect(e.reportRenderer.visible).ok();
  await t.expect(e.shareHeader.textContent).contains('Invoice Pipeline');
});

test('version selection', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');

  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(e.definitionSelection);

  await t.takeElementScreenshot(
    e.definitionSelectionDialog,
    'process/single-report/report-processDefinitionSelection.png'
  );

  await t.click(e.versionPopover);
  await t.click(e.versionAll);

  const allNumber = +(await e.reportNumber.textContent);

  await t.click(e.versionLatest);

  const latestNumber = +(await e.reportNumber.textContent);

  await t.click(e.versionSpecific);
  await t.click(e.versionCheckbox(5));
  await t.click(e.versionCheckbox(3));
  await t.click(e.versionCheckbox(2));

  await t.takeElementScreenshot(
    e.definitionSelectionDialog,
    'process/single-report/report-versionSelection.png'
  );

  const rangeNumber = +(await e.reportNumber.textContent);

  await t.expect(allNumber > rangeNumber).ok();
  await t.expect(rangeNumber > latestNumber).ok();

  await t.click(e.tenantPopover);

  await t.takeElementScreenshot(
    e.definitionSelectionDialog,
    'process/single-report/tenantSelection.png'
  );
});

test('raw data table pagination', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');
  await t.click(e.nextPageButton);
  await t.click(e.rowsPerPageButton);
  await t.click(e.option('100'));
  await t.expect(e.reportTable.visible).ok();
});

test('sort table columns', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');

  await t.typeText(e.nameEditField, 'Table Report', {replace: true});

  await t.expect(e.reportRenderer.textContent).contains('invoice');
  await t.expect(e.reportRenderer.textContent).contains('Start Date');

  await t.click(e.tableHeader(9));

  await t
    .resizeWindow(1600, 650)
    .takeScreenshot('process/single-report/sorting.png', {fullPage: true})
    .maximizeWindow();

  let a, b, c;

  a = await e.tableCell(0, 9);
  b = await e.tableCell(1, 9);
  c = await e.tableCell(2, 9);

  await t.expect(a <= b).ok();
  await t.expect(b <= c).ok();

  await t.click(e.tableHeader(9));

  a = await e.tableCell(0, 9);
  b = await e.tableCell(1, 9);
  c = await e.tableCell(2, 9);

  await t.expect(a >= b).ok();
  await t.expect(b >= c).ok();
});

test('exclude raw data columns', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');

  await t.resizeWindow(1600, 750);

  await t.click(e.configurationButton);

  await t.click(e.selectSwitchLabel('Start Date'));
  await t.click(e.selectSwitchLabel('Show Instance Count'));
  await t.click(e.selectSwitchLabel('Process Definition Key'));
  await t.click(e.selectSwitchLabel('Business Key'));
  await t.click(e.selectSwitchLabel('End Date'));
  await t.click(e.selectSwitchLabel('approved'));

  await t
    .takeElementScreenshot(e.controlPanel, 'process/single-report/rawdata.png')
    .maximizeWindow();

  await t.expect(e.reportRenderer.textContent).notContains('Start Date');
});

test('cancel changes', async (t) => {
  await u.createNewReport(t);

  await u.save(t);

  await t.click(e.editButton);
  await t.typeText(e.nameEditField, 'Another new Name', {replace: true});
  await u.cancel(t);

  await t.expect(e.reportName.textContent).notEql('Another new Name');
});

test('update definition', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');

  await u.selectDefinition(t, 'Lead Qualification');

  await t.expect(e.reportRenderer.textContent).notContains('invoice');
  await t.expect(e.reportRenderer.textContent).contains('leadQualification');
});

test('should only enable valid combinations for process instance count grouped by none', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(e.groupbyDropdown);

  await t.expect(e.option('None').hasClass('disabled')).notOk();
  await t.expect(e.option('Start Date').hasClass('disabled')).notOk();
  await t.expect(e.option('Variable').hasClass('disabled')).notOk();
  await t.expect(e.option('Flow Nodes').hasClass('disabled')).ok();

  await t.click(e.option('None'));

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Number').hasClass('disabled')).notOk();
  await t.expect(e.option('Table').hasClass('disabled')).ok();
  await t.expect(e.option('Bar Chart').hasClass('disabled')).ok();
  await t.expect(e.option('Heatmap').hasClass('disabled')).ok();

  await t.expect(e.reportNumber.visible).ok();
});

test('Limit the precsion in number report', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');

  await t.typeText(e.nameEditField, 'Number Report', {replace: true});

  await u.selectView(t, 'Process Instance', 'Duration');

  await t.click(e.configurationButton);
  await t.click(e.limitPrecisionSwitch);
  await t.typeText(e.limitPrecisionInput, '2', {replace: true});

  await t
    .resizeWindow(1600, 800)
    .takeScreenshot('process/single-report/NumberConfiguration.png', {fullPage: true})
    .maximizeWindow();

  await t.expect(e.reportNumber.visible).ok();
});

test('Disable absolute and relative values for table reports', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');

  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'Start Date', 'Month');

  await u.selectVisualization(t, 'Table');
  await t.click(e.configurationButton);
  await t.click(e.selectSwitchLabel('Show Absolute Value'));
  await t.click(e.selectSwitchLabel('Show Relative Value'));

  await t.expect(e.reportTable.textContent).contains('Start Date: Month');
  await t.expect(e.reportTable.textContent).notContains('Process Instance: Count');
  await t.expect(e.reportTable.textContent).notContains('Relative Frequency');
});

test('select process instance count grouped by end date', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Embedded Subprocess');
  await u.selectView(t, 'Process Instance', 'Count');

  await u.selectGroupby(t, 'End Date', 'Automatic');

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Number').hasClass('disabled')).ok();
  await t.expect(e.option('Table').hasClass('disabled')).notOk();
  await t.expect(e.option('Bar Chart').hasClass('disabled')).notOk();
  await t.expect(e.option('Line Chart').hasClass('disabled')).notOk();
  await t.expect(e.option('Pie Chart').hasClass('disabled')).notOk();
  await t.expect(e.option('Heatmap').hasClass('disabled')).ok();
  await t.click(e.visualizationDropdown);

  await u.selectVisualization(t, 'Table');
  await t.expect(e.reportTable.visible).ok();
});

test('select process instance count grouped by variable', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');

  await u.selectView(t, 'Process Instance', 'Count');

  await u.selectGroupby(t, 'Variable', 'amount');

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Number').hasClass('disabled')).ok();
  await t.expect(e.option('Table').hasClass('disabled')).notOk();
  await t.expect(e.option('Bar Chart').hasClass('disabled')).notOk();
  await t.expect(e.option('Line Chart').hasClass('disabled')).notOk();
  await t.expect(e.option('Pie Chart').hasClass('disabled')).notOk();
  await t.expect(e.option('Heatmap').hasClass('disabled')).ok();

  await t.click(e.visualizationDropdown);

  await u.selectVisualization(t, 'Table');
  await t.expect(e.reportTable.textContent).contains('Variable: amount');
});

test('variable report', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');

  await u.selectView(t, 'Variable', 'amount');

  await t.expect(e.reportNumber.visible).ok();

  await t.click(e.configurationButton);
  await t.click(e.limitPrecisionSwitch);
  await t.typeText(e.limitPrecisionInput, '2', {replace: true});

  await t.expect(e.reportNumber.visible).ok();
});

test('should only enable valid combinations for Flow Node Count', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');

  await u.selectView(t, 'Flow Node', 'Count');

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Number').hasClass('disabled')).ok();
  await t.expect(e.option('Table').hasClass('disabled')).notOk();
  await t.expect(e.option('Bar Chart').hasClass('disabled')).notOk();
  await t.expect(e.option('Heatmap').hasClass('disabled')).notOk();
});

test('select which flow nodes to show from the configuration', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Flow Node', 'Count');

  await t.expect(e.nodeTableCell('Assign Approver Group').exists).ok();

  await t.resizeWindow(1150, 800);

  await t.click(e.configurationButton);

  await t.click(e.showFlowNodesSwitch);
  await t.click(e.showFlowNodes);
  await t.click(e.deselectAllButton);

  await t.click(e.flowNode('approveInvoice'));
  await t.click(e.flowNode('reviewInvoice'));
  await t.click(e.flowNode('prepareBankTransfer'));

  await t
    .takeElementScreenshot(e.modalContainer, 'process/single-report/shownNodes.png')
    .maximizeWindow();

  await t.click(e.primaryModalButton);

  await t.expect(e.nodeTableCell('Assign Approver Group').exists).notOk();
});

test('bar/line chart configuration', async (t) => {
  await u.createNewReport(t);
  await t.typeText(e.nameEditField, 'Bar Chart Report', {replace: true});

  await u.selectDefinition(t, 'Multi-Instance Subprocess', 'All');

  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'Start Date', 'Automatic');
  await u.selectVisualization(t, 'Bar Chart');

  await t.resizeWindow(1600, 800);

  await t.click(e.configurationButton);
  await t.click(e.cyanColor);

  await t.takeScreenshot('process/single-report/chartConfiguration.png', {fullPage: true});

  await t.click(e.goalSwitch);

  await t.typeText(e.chartGoalInput, '4.5', {replace: true});
  await t.expect(e.chartGoalInput.hasAttribute('disabled')).notOk();

  await t.expect(e.reportChart.visible).ok();

  await t.takeScreenshot('process/single-report/targetValue.png', {fullPage: true});

  await t.click(e.configurationButton);
  await u.selectVisualization(t, 'Line Chart');

  await t.takeElementScreenshot(e.reportRenderer, 'process/single-report/targetline.png');

  await t.maximizeWindow();

  await t.click(e.configurationButton);

  await t.typeText(e.axisInputs('X Axis Label'), 'x axis label');
  await t.typeText(e.axisInputs('Y Axis Label'), 'y axis label');

  await t.expect(e.reportChart.visible).ok();
});

test('different visualizations', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Lead Qualification');
  await u.selectView(t, 'Flow Node', 'Duration');

  await t.expect(e.reportTable.visible).ok();

  await u.selectVisualization(t, 'Bar Chart');

  await t.expect(e.reportTable.exists).notOk();
  await t.expect(e.reportChart.visible).ok();

  await u.selectVisualization(t, 'Heatmap');

  await t.expect(e.reportChart.exists).notOk();
  await t.expect(e.reportDiagram.visible).ok();

  await u.selectView(t, 'Process Instance', 'Duration');

  await t.expect(e.reportDiagram.exists).notOk();
  await t.expect(e.reportNumber.visible).ok();
});

test('aggregators', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Embedded Subprocess');
  await u.selectView(t, 'Process Instance', 'Duration');

  await t.click(e.filterButton);
  await t.click(e.filterOption('Instance state'));
  await t.click(e.subFilterOption('Completed Instances Only'));

  const avg = await e.reportNumber.textContent;

  await t.resizeWindow(1600, 800);

  await t.click(e.configurationButton);

  await t.click(e.limitPrecisionSwitch);
  await t.typeText(e.limitPrecisionInput, '2', {replace: true});

  await t.click(e.configurationButton);

  await t.click(e.aggregationTypeSelect);

  await t.takeScreenshot('process/single-report/durationAggregation.png', {fullPage: true});

  await t.click(e.aggregationOption('Minimum'));

  await t.click(e.configurationButton);
  await t.click(e.limitPrecisionSwitch);
  await t.click(e.configurationButton);

  await t.maximizeWindow();

  const min = await e.reportNumber.textContent;

  await u.selectAggregation(t, 'Maximum');

  const max = await e.reportNumber.textContent;

  await t.expect(min).notEql(avg);
  await t.expect(avg).notEql(max);
});

test('progress bar and reset to default', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Lead Qualification');

  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(e.configurationButton);

  await t.click(e.goalSwitch);
  await t.typeText(e.goalTargetInput, '100', {replace: true});

  await t.click(e.configurationButton);

  await t.expect(e.reportProgressBar.visible).ok();

  await t
    .resizeWindow(1000, 530)
    .takeElementScreenshot(e.reportRenderer, 'process/single-report/progressbar.png')
    .maximizeWindow();

  await t.click(e.configurationButton);
  await t.typeText(e.goalTargetInput, '50', {replace: true});
  await t.click(e.configurationButton);

  await t
    .resizeWindow(1000, 530)
    .takeElementScreenshot(e.reportRenderer, 'process/single-report/progressbarExceeded.png')
    .maximizeWindow();

  await t.click(e.configurationButton);
  await t.click(e.resetButton);
  await t.click(e.configurationButton);

  await t.expect(e.reportProgressBar.visible).notOk();
  await t.expect(e.reportNumber.visible).ok();
});

test('heatmap target values', async (t) => {
  await u.createNewReport(t);

  await t.typeText(e.nameEditField, 'Invoice Pipeline', {replace: true});

  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Flow Node', 'Duration');

  await t.resizeWindow(1650, 850);

  await u.selectVisualization(t, 'Heatmap');

  await t.hover(e.flowNode('approveInvoice'));

  await t.expect(e.tooltip.textContent).notContains('Target\u00A0duration');

  await t.click(e.targetValueButton);
  await t.typeText(e.targetValueInput('Approve Invoice'), '1');
  await t.typeText(e.targetValueInput('Prepare Bank Transfer'), '5');
  await t.typeText(e.targetValueInput('Review Invoice'), '1');

  await t.takeElementScreenshot(e.modalContainer, 'process/single-report/targetvalue-2.png');

  await t.click(e.primaryModalButton);

  await t.hover(e.flowNode('approveInvoice'));

  await t.expect(e.tooltip.textContent).contains('Target\u00A0duration:\u00A01h');

  await addAnnotation(e.targetValueButton, 'Toggle Target Value Mode');
  await addAnnotation(e.tooltip, 'Target Value Tooltip', {x: -50, y: 0});
  await addAnnotation(
    e.flowNode('prepareBankTransfer'),
    'Activity with Duration above\nTarget Value',
    {x: -50, y: 0}
  );
  await addAnnotation(e.badge('prepareBankTransfer'), 'Target Value for Activity', {x: 50, y: 0});

  await t.takeScreenshot('process/single-report/targetvalue-1.png', {fullPage: true});

  await clearAllAnnotations();

  await t.click(e.targetValueButton);

  await t.hover(e.flowNode('approveInvoice'));

  await t.expect(e.tooltip.textContent).notContains('target\u00A0duration');

  await t.maximizeWindow();
});

test('always show tooltips', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Flow Node', 'Count');

  await t.resizeWindow(1650, 850);

  await u.selectVisualization(t, 'Heatmap');

  await t.expect(e.tooltip.exists).notOk();

  await t.click(e.configurationButton);
  await t.click(e.selectSwitchLabel('Show Absolute Value'));
  await t.click(e.selectSwitchLabel('Show Relative Value'));

  await t.takeScreenshot('process/single-report/heatmap.png', {fullPage: true}).maximizeWindow();

  await t.expect(e.tooltip.visible).ok();
});

test('should only enable valid combinations for user task', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');

  await u.selectView(t, 'User Task', 'Count');

  await t.click(e.groupbyDropdown);

  await t.expect(e.option('None').hasClass('disabled')).ok();
  await t.expect(e.option('Flow Nodes').hasClass('disabled')).ok();
  await t.expect(e.option('User Task').hasClass('disabled')).notOk();
  await t.expect(e.option('Assignee').hasClass('disabled')).notOk();
  await t.expect(e.option('Candidate Group').hasClass('disabled')).notOk();
  await t.expect(e.option('Start Date').hasClass('disabled')).notOk();

  await t.click(e.option('User Tasks'));

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Number').hasClass('disabled')).ok();
  await t.expect(e.option('Table').hasClass('disabled')).notOk();
  await t.expect(e.option('Bar Chart').hasClass('disabled')).notOk();
  await t.expect(e.option('Heatmap').hasClass('disabled')).notOk();

  await u.selectGroupby(t, 'Assignee');

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Heatmap').hasClass('disabled')).ok();

  await t.click(e.option('Table'));

  await t.expect(e.reportTable.visible).ok();
});

test('should be able to distribute candidate group by user task', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Lead Qualification', 'All');
  await u.selectView(t, 'User Task', 'Count');

  await u.selectGroupby(t, 'Candidate Group');

  await u.selectVisualization(t, 'Pie Chart');

  await t.click(e.distributedBySelect);

  await t.click(e.dropdownOption('User Task'));

  await t.expect(e.visualizationDropdown.textContent).contains('Bar Chart');

  await t.takeElementScreenshot(e.reportRenderer, 'process/single-report/distributed-report.png');

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Table').hasClass('disabled')).notOk();
  await t.expect(e.option('Bar Chart').hasClass('disabled')).notOk();
  await t.expect(e.option('Line Chart').hasClass('disabled')).notOk();
  await t.expect(e.option('Number').hasClass('disabled')).ok();
  await t.expect(e.option('Pie Chart').hasClass('disabled')).ok();

  await t.click(e.option('Table'));
  await t.expect(e.reportTable.visible).ok();
});

test('should be able to select how the time of the user task is calculated', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'User Task', 'Duration');
  await u.selectGroupby(t, 'Candidate Group');
  await u.selectVisualization(t, 'Table');

  await t.click(e.userTaskDurationSelect);

  await t.click(e.option('Idle'));
  await t.expect(e.reportTable.visible).ok();

  await t.click(e.userTaskDurationSelect);

  await t.click(e.option('Work'));
  await t.expect(e.reportTable.visible).ok();
});

test('show process instance count', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');

  await t.click(e.configurationButton);
  await t.click(e.instanceCountSwitch);

  await t.expect(e.instanceCount.visible).ok();
  await t.expect(e.instanceCount.textContent).contains('Total Instance Count:');
});

test('process parts', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');

  await u.selectView(t, 'Process Instance', 'Duration');

  const withoutPart = await e.reportNumber.textContent;

  await t.resizeWindow(1150, 700);

  await t.click(e.processPartButton);
  await t.click(e.modalFlowNode('StartEvent_1'));
  await t.click(e.modalFlowNode('assignApprover'));

  await t.takeElementScreenshot(e.modalContainer, 'process/single-report/process-part.png');
  await t.maximizeWindow();

  await t.click(e.primaryModalButton);

  const withPart = await e.reportNumber.textContent;

  await t.expect(withoutPart).notEql(withPart);
});

test('deleting', async (t) => {
  await u.createNewReport(t);

  await u.save(t);
  await t.click(e.deleteButton);
  await t.click(e.modalConfirmbutton);

  await t.expect(e.report.exists).notOk();
});

test('show raw data and process model', async (t) => {
  await u.createNewReport(t);
  await u.save(t);

  await t.click(e.detailsPopoverButton);
  await t.click(e.modalButton('View Raw data'));
  await t.expect(e.rawDataTable.visible).ok();
  await t.click(e.closeModalButton);

  await t.click(e.detailsPopoverButton);
  await t.click(e.modalButton('View Process Model'));
  await t.expect(e.modalDiagram.visible).ok();
});

test('group by duration', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'Duration');
  await u.selectVisualization(t, 'Bar Chart');

  await t.expect(e.reportChart.visible).ok();

  await t.click(e.configurationButton);
  await t.click(e.bucketSizeSwitch);
  await t.click(e.bucketSizeUnitSelect);
  await t.click(e.configurationOption('days'));
  await t.click(e.configurationButton);

  await t.expect(e.reportChart.visible).ok();

  await u.selectView(t, 'Flow Node', 'Count');

  await t.expect(e.reportChart.visible).ok();

  await t.click(e.distributedBySelect);
  await t.click(e.dropdownOption('Flow Node'));
  await u.selectVisualization(t, 'Table');

  await t.expect(e.reportRenderer.textContent).contains('Invoice\nprocessed');

  await u.selectView(t, 'User Task', 'Count');

  await t.expect(e.reportRenderer.textContent).notContains('Invoice processed');
  await t.expect(e.reportRenderer.textContent).contains('User Task: Count');
});

test('distribute by variable', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'Start Date', 'Automatic');
  await u.selectVisualization(t, 'Bar Chart');

  await t.click(e.distributedBySelect);
  await t.click(e.dropdownOption('Variable'));
  await t.click(e.submenuOption('approved'));
  await t
    .resizeWindow(1650, 960)
    .takeElementScreenshot(e.reportRenderer, 'process/single-report/distributedByVar.png')
    .maximizeWindow();

  await t.click(e.distributedBySelect);
  await t.click(e.dropdownOption('Variable'));
  await t.click(e.submenuOption('invoiceCategory'));
  await u.selectVisualization(t, 'Table');

  await t.expect(e.reportRenderer.textContent).contains('Misc');

  await u.selectView(t, 'Flow Node', 'Count');

  await t.expect(e.reportRenderer.textContent).notContains('Misc');
});

test('distribute by start/end date', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'Variable', 'invoiceCategory');
  await u.selectVisualization(t, 'Bar Chart');
  await t.click(e.distributedBySelect);
  await t.click(e.dropdownOption('Start Date'));
  await t.click(e.submenuOption('month'));
  await t.click(e.distributedBySelect);
  await t.click(e.dropdownOption('End Date'));
  await t.click(e.submenuOption('automatic'));
  await u.selectGroupby(t, 'Variable', 'boolVar');

  await t.expect(e.reportChart.visible).ok();
});

test('incident reports', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Incident Process', 'All');
  await u.selectView(t, 'Incident', 'Count');

  await t.expect(e.reportNumber.visible).ok();

  await u.selectView(t, 'Incident', 'Resolution Duration');

  await t.expect(e.reportNumber.visible).ok();

  await u.selectGroupby(t, 'Flow Nodes');
  await u.selectVisualization(t, 'Bar Chart');

  await t.expect(e.reportChart.visible).ok();

  await u.selectVisualization(t, 'Table');

  await t.expect(e.reportRenderer.textContent).contains('Incident: Resolution Duration');
});
