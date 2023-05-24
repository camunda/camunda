/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';
import {addAnnotation, clearAllAnnotations} from '../browserMagic';

import * as e from './ProcessReport.elements.js';
import * as Common from './Common.elements.js';

fixture('Process Report').page(config.endpoint).beforeEach(u.login).afterEach(cleanEntities);

test('create a report from a template', async (t) => {
  await t.resizeWindow(1300, 750);
  await t.click(Common.createNewMenu);
  await t.click(Common.newReportOption);
  await t.click(Common.submenuOption('Process Report'));

  await t.click(Common.templateModalProcessField);
  await t.click(Common.option('Invoice Receipt with alternative correlation variable'));

  await t.click(e.templateOption('Heatmap: Flownode count'));

  await t.takeScreenshot('img/reportTemplate.png', {fullPage: true});
  await t.maximizeWindow();

  await t.click(Common.modalConfirmButton);

  await t.expect(Common.nameEditField.value).eql('Heatmap: Flownode count');
  await t.expect(e.groupbyDropdownButton.textContent).contains('Flow Nodes');
  await t.expect(e.reportDiagram.visible).ok();
});

test('create and name a report', async (t) => {
  await u.createNewReport(t);

  await t.typeText(Common.nameEditField, 'Invoice Pipeline', {replace: true});

  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Flow Node', 'Count');

  await t.resizeWindow(1350, 750);

  await u.selectVisualization(t, 'Heatmap');

  await t.takeScreenshot('img/report-reportEditActions.png', {fullPage: true});
  await t.maximizeWindow();

  await u.save(t);

  await t.expect(e.reportName.textContent).eql('Invoice Pipeline');
});

test('sharing', async (t) => {
  await u.createNewReport(t);

  await t.typeText(Common.nameEditField, 'Invoice Pipeline', {replace: true});

  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Flow Node', 'Count');

  await t.resizeWindow(1000, 650);

  await u.selectVisualization(t, 'Heatmap');
  await u.save(t);

  await t.expect(Common.shareButton.hasClass('disabled')).notOk();

  await t.click(Common.shareButton);
  await t.click(Common.shareSwitch);

  await t
    .takeScreenshot('process-analysis/report-analysis/img/report-sharingPopover.png', {
      fullPage: true,
    })
    .maximizeWindow();

  const shareUrl = await Common.shareUrl.value;

  await t.navigateTo(shareUrl);

  await t.expect(e.reportRenderer.visible).ok();
  await t.expect(Common.shareHeader.textContent).contains('Invoice Pipeline');
});

test('sharing header parameters', async (t) => {
  await u.createNewReport(t);

  await u.save(t);

  await t.click(Common.shareButton);
  await t.click(Common.shareSwitch);

  const shareUrl = await Common.shareUrl.value;

  await t.navigateTo(shareUrl + '?mode=embed');

  await t.expect(Common.shareOptimizeIcon.visible).ok();
  await t.expect(Common.shareTitle.visible).ok();
  await t.expect(Common.shareLink.visible).ok();

  await t.navigateTo(shareUrl + '?mode=embed&header=hidden');

  await t.expect(Common.shareHeader.exists).notOk();

  await t.navigateTo(shareUrl + '?header=titleOnly');

  await t.expect(Common.shareTitle.exists).ok();
  await t.expect(Common.shareLink.exists).notOk();

  await t.navigateTo(shareUrl + '?mode=embed&header=linkOnly');

  await t.expect(Common.shareTitle.exists).notOk();
  await t.expect(Common.shareLink.exists).ok();
});

test('version selection', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(e.definitionEditor);
  await t.click(e.versionPopover);
  await t.click(e.versionAll);

  const allNumber = +(await e.reportNumber.textContent);

  await t.click(e.versionLatest);

  const latestNumber = +(await e.reportNumber.textContent);

  await t.click(e.versionSpecific);
  await t.click(e.versionCheckbox(0));
  await t.click(e.versionCheckbox(1));
  await t.click(e.versionCheckbox(2));

  await t.takeElementScreenshot(
    e.definitionSelectionDialog,
    'process-analysis/report-analysis/img/report-versionSelection.png'
  );

  const rangeNumber = +(await e.reportNumber.textContent);

  await t.expect(allNumber > rangeNumber).ok();
  await t.expect(rangeNumber > latestNumber).ok();

  await t.click(e.tenantPopover);

  await t.takeElementScreenshot(
    e.definitionSelectionDialog,
    'process-analysis/report-analysis/img/tenantSelection.png'
  );
});

test('raw data table pagination', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Raw Data');
  await t.click(e.nextPageButton);
  await t.click(e.rowsPerPageButton);
  await t.click(e.rowsPerPageOption('100'));
  await t.expect(e.reportTable.visible).ok();
});

test('sort table columns', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Raw Data');

  await t.typeText(Common.nameEditField, 'Table Report', {replace: true});

  await t.expect(e.reportRenderer.textContent).contains('invoice');
  await t.expect(e.reportRenderer.textContent).contains('Start Date');

  await t.click(e.tableHeader(9));

  await t
    .resizeWindow(1600, 650)
    .takeScreenshot('process-analysis/report-analysis/img/sorting.png', {fullPage: true})
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

test('drag raw data table columns', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Raw Data');

  const originalPositionText = await e.tableHeader(3).textContent;
  await t.drag(e.tableHeader(3), 350, 0);
  const newPositionText = await e.tableHeader(4).textContent;
  await t.expect(originalPositionText).eql(newPositionText);
});

test('view a variable object in rawdata table', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Raw Data');

  await t.scrollIntoView(e.objectViewBtn);

  await t.click(e.objectViewBtn);
  await t.expect(e.objectVariableModal.visible).ok();

  await t.click(e.objectVariableModalCloseButton);
});

test('drag distributed table columns', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'User Task', 'Count');
  await u.selectGroupby(t, 'Candidate Group');
  await u.selectVisualization(t, 'Table');

  const originalPositionText = await e.tableGroup(1).textContent;
  await t.drag(e.tableHeader(1), 600, 0);
  const newPositionText = await e.tableGroup(2).textContent;
  await t.expect(originalPositionText).eql(newPositionText);
});

test('exclude raw data columns', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Raw Data');

  await t.resizeWindow(1600, 750);

  await t.click(e.configurationButton);

  await t.click(e.selectSwitchLabel('Start Date'));
  await t.click(e.selectSwitchLabel('Show Instance Count'));
  await t.click(e.selectSwitchLabel('Process Definition Key'));
  await t.click(e.selectSwitchLabel('Business Key'));
  await t.click(e.selectSwitchLabel('End Date'));

  await t.click(e.selectSectionWithLabel('VARIABLES'));

  await t.click(e.selectSwitchLabel('approved'));

  await t.takeScreenshot('process-analysis/report-analysis/img/rawdata.png').maximizeWindow();

  await t.expect(e.reportRenderer.textContent).notContains('Start Date');
});

test('cancel changes', async (t) => {
  await u.createNewReport(t);

  await u.save(t);

  await t.click(Common.editButton);
  await t.typeText(Common.nameEditField, 'Another new Name', {replace: true});
  await u.cancel(t);

  await t.expect(e.reportName.textContent).notEql('Another new Name');
});

test('should only enable valid combinations for process instance count grouped by none', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(e.groupbyDropdown);

  await t.expect(Common.option('Start Date').hasClass('disabled')).notOk();
  await t.expect(Common.option('Variable').hasClass('disabled')).notOk();
  await t.expect(Common.option('Flow Nodes').hasClass('disabled')).ok();

  await t.click(e.visualizationDropdown);

  await t.expect(Common.option('Number').hasClass('disabled')).notOk();
  await t.expect(Common.option('Table').hasClass('disabled')).ok();
  await t.expect(Common.option('Bar Chart').hasClass('disabled')).ok();
  await t.expect(Common.option('Heatmap').hasClass('disabled')).ok();
  await t.expect(Common.option('Bar/Line Chart').hasClass('disabled')).ok();

  await t.expect(e.reportNumber.visible).ok();
});

test('Limit the precision in number report', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await t.typeText(Common.nameEditField, 'Number Report', {replace: true});

  await u.selectView(t, 'Process Instance', 'Duration');

  await t.click(e.configurationButton);
  await t.click(e.limitPrecisionSwitch);
  await t.typeText(e.limitPrecisionInput, '2', {replace: true});

  await t
    .resizeWindow(1600, 800)
    .takeScreenshot('process-analysis/report-analysis/img/NumberConfiguration.png', {
      fullPage: true,
    })
    .maximizeWindow();

  await t.expect(e.reportNumber.visible).ok();
});

test('Limit the precision in chart type reports', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await t.typeText(Common.nameEditField, 'Chart Report', {replace: true});

  await u.selectView(t, 'Flow Node', 'Duration');
  await u.selectVisualization(t, 'Bar Chart');

  await t.click(e.configurationButton);
  await t.click(e.limitPrecisionSwitch);
  await t.typeText(e.limitPrecisionInput, '2', {replace: true});

  await u.selectVisualization(t, 'Bar Chart');
  await t.click(e.configurationButton);
  await t.expect(e.limitPrecisionInput.visible).ok();
  await t.expect(e.reportChart.visible).ok();

  // Heatmap
  await u.selectVisualization(t, 'Heatmap');
  await t.click(e.configurationButton);
  await t.expect(e.limitPrecisionInput.visible).ok();
  await t.expect(e.reportDiagram.visible).ok();
});

test('Disable absolute and relative values for table reports', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'Start Date', 'Month');

  await u.selectVisualization(t, 'Table');
  await t.click(e.configurationButton);
  await t.click(e.selectSwitchLabel('Show Absolute Value'));
  await t.click(e.selectSwitchLabel('Show Relative Value'));

  await t.expect(e.reportTable.textContent).contains('Start Date');
  await t.expect(e.reportTable.textContent).notContains('Process Instance: Count');
  await t.expect(e.reportTable.textContent).notContains('Relative Frequency');
});

test('select process instance count grouped by end date', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Embedded Subprocess');
  await u.selectView(t, 'Process Instance', 'Count');

  await u.selectGroupby(t, 'End Date', 'Automatic');

  await t.click(e.visualizationDropdown);

  await t.expect(Common.option('Number').hasClass('disabled')).ok();
  await t.expect(Common.option('Table').hasClass('disabled')).notOk();
  await t.expect(Common.option('Bar Chart').hasClass('disabled')).notOk();
  await t.expect(Common.option('Line Chart').hasClass('disabled')).notOk();
  await t.expect(Common.option('Pie Chart').hasClass('disabled')).notOk();
  await t.expect(Common.option('Heatmap').hasClass('disabled')).ok();
  await t.expect(Common.option('Bar/Line Chart').hasClass('disabled')).ok();
  await t.click(e.visualizationDropdown);

  await u.selectVisualization(t, 'Table');
  await t.expect(e.reportTable.visible).ok();
});

test('select process instance count grouped by variable', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await u.selectView(t, 'Process Instance', 'Count');

  await u.selectGroupby(t, 'Variable', 'amount');

  await t.click(e.visualizationDropdown);

  await t.expect(Common.option('Number').hasClass('disabled')).ok();
  await t.expect(Common.option('Table').hasClass('disabled')).notOk();
  await t.expect(Common.option('Bar Chart').hasClass('disabled')).notOk();
  await t.expect(Common.option('Line Chart').hasClass('disabled')).notOk();
  await t.expect(Common.option('Pie Chart').hasClass('disabled')).notOk();
  await t.expect(Common.option('Heatmap').hasClass('disabled')).ok();
  await t.expect(Common.option('Bar/Line Chart').hasClass('disabled')).ok();

  await t.click(e.visualizationDropdown);

  await u.selectVisualization(t, 'Table');
  await t.expect(e.reportTable.textContent).contains('Process Instance Var: amount');
});

test('variable report', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await u.selectView(t, 'Variable', 'amount');

  await t.expect(e.reportNumber.visible).ok();

  await t.click(e.configurationButton);
  await t.click(e.limitPrecisionSwitch);
  await t.typeText(e.limitPrecisionInput, '2', {replace: true});

  await t.expect(e.reportNumber.visible).ok();
});

test('should only enable valid combinations for Flow Node Count', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await u.selectView(t, 'Flow Node', 'Count');

  await t.click(e.visualizationDropdown);

  await t.expect(Common.option('Number').hasClass('disabled')).ok();
  await t.expect(Common.option('Table').hasClass('disabled')).notOk();
  await t.expect(Common.option('Bar Chart').hasClass('disabled')).notOk();
  await t.expect(Common.option('Heatmap').hasClass('disabled')).notOk();
});

test('bar chart and line chart configuration', async (t) => {
  await u.createNewReport(t);
  await t.typeText(Common.nameEditField, 'Bar Chart Report', {replace: true});

  await u.selectReportDefinition(t, 'Multi-Instance Subprocess');

  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'Start Date', 'Automatic');
  await u.selectVisualization(t, 'Bar Chart');

  await t.resizeWindow(1600, 800);

  await t.click(e.configurationButton);
  await t.click(e.cyanColor);

  await t.takeScreenshot('process-analysis/report-analysis/img/chartConfiguration.png', {
    fullPage: true,
  });

  await t.click(e.goalSwitch);

  await t.typeText(e.chartGoalInput, '4.5', {replace: true});
  await t.expect(e.chartGoalInput.hasAttribute('disabled')).notOk();

  await t.expect(e.reportChart.visible).ok();

  await t.takeScreenshot('process-analysis/report-analysis/img/targetValue.png', {fullPage: true});

  await u.selectVisualization(t, 'Line Chart');

  await t.takeElementScreenshot(
    e.reportRenderer,
    'process-analysis/report-analysis/img/targetline.png'
  );

  await t.maximizeWindow();

  await t.click(e.configurationButton);

  await t.click(e.selectSwitchLabel('Logarithmic Scale'));

  await t.typeText(e.axisInputs('X Axis Label'), 'x axis label', {replace: true});
  await t.typeText(e.axisInputs('Y Axis Label'), 'y axis label', {replace: true});

  await t.click(e.selectSwitchLabel('Logarithmic Scale'));

  await t.expect(e.reportChart.visible).ok();

  await t.click(e.configurationButton);

  await t.click(e.distributedBySelect);
  await t.click(e.dropdownOption('Variable'));
  await t.click(Common.submenuOption('boolVar'));
  await u.selectVisualization(t, 'Bar Chart');

  await t.click(e.configurationButton);

  await t.click(e.selectSwitchLabel('Stacked bars'));

  await t
    .resizeWindow(1600, 800)
    .takeScreenshot('process-analysis/report-analysis/img/stackedBar.png', {fullPage: true});

  await t.click(e.configurationButton);

  await t.click(e.addMeasureButton);
  await t.click(e.dropdownOption('Duration'));
  await u.selectVisualization(t, 'Bar/Line Chart');

  await t.click(e.configurationButton);

  await t.takeScreenshot('process-analysis/report-analysis/img/barLine.png', {fullPage: true});

  await t.click(e.lineButton);

  await t.expect(e.reportChart.visible).ok();

  await t.maximizeWindow();
});

test('horizontal bar chart', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Lead Qualification');
  await u.selectView(t, 'User Task', 'Duration');
  await u.selectVisualization(t, 'Bar Chart');

  await t.resizeWindow(1600, 800);

  await t.click(e.configurationButton);

  await t.takeScreenshot('process-analysis/report-analysis/img/horizontalBar.png', {
    fullPage: true,
  });

  await t.click(e.selectSwitchLabel('Horizontal bars'));

  await t.maximizeWindow();
});

test('different visualizations', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Lead Qualification');
  await u.selectView(t, 'Flow Node', 'Duration');
  await u.selectVisualization(t, 'Table');

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
  await u.selectReportDefinition(t, 'Embedded Subprocess');
  await u.selectView(t, 'Process Instance', 'Duration');

  await t.click(e.sectionToggle('Filters'));
  await t.click(e.filterButton);
  await t.click(e.filterOption('Instance State'));
  await t.click(e.modalOption('Completed'));
  await t.click(Common.modalConfirmButton);

  const avg = await e.reportNumber.textContent;

  await t.resizeWindow(1600, 800);

  await t.click(e.configurationButton);

  await t.click(e.limitPrecisionSwitch);
  await t.typeText(e.limitPrecisionInput, '2', {replace: true});
  await t.click(e.configurationButton);

  await t.click(e.aggregationTypeSelect);

  await t.takeScreenshot('process-analysis/report-analysis/img/durationAggregation.png', {
    fullPage: true,
  });

  await t.click(e.aggregationOption('Minimum'));
  await t.click(e.aggregationOption('Average'));

  await t.click(e.configurationButton);
  await t.click(e.limitPrecisionSwitch);
  await t.click(e.configurationButton);

  await t.maximizeWindow();

  const min = await e.reportNumber.textContent;

  await t.click(e.aggregationTypeSelect);
  await t.click(e.aggregationOption('Maximum'));
  await t.click(e.aggregationOption('Minimum'));

  const max = await e.reportNumber.textContent;

  await t.click(e.aggregationOption('P99'));
  await t.click(e.aggregationOption('Maximum'));

  const percentile = await e.reportNumber.textContent;

  await t.expect(min).notEql(avg);
  await t.expect(avg).notEql(max);
  await t.expect(percentile).notEql(max);
});

test('progress bar and reset to default', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Lead Qualification');

  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(e.configurationButton);

  await t.click(e.goalSwitch);
  await t.typeText(e.goalTargetInput, '200', {replace: true});

  await t.click(e.configurationButton);

  await t.expect(e.reportProgressBar.visible).ok();

  await t
    .resizeWindow(1000, 530)
    .takeElementScreenshot(
      e.reportProgressBar,
      'process-analysis/report-analysis/img/progressbar.png'
    )
    .maximizeWindow();

  await t.click(e.configurationButton);
  await t.typeText(e.goalTargetInput, '50', {replace: true});
  await t.click(e.configurationButton);

  await t
    .resizeWindow(1000, 530)
    .takeElementScreenshot(
      e.reportProgressBar,
      'process-analysis/report-analysis/img/progressbarExceeded.png'
    )
    .maximizeWindow();

  await t.click(e.configurationButton);
  await t.click(e.resetButton);
  await t.click(e.configurationButton);

  await t.expect(e.reportProgressBar.visible).notOk();
  await t.expect(e.reportNumber.visible).ok();
});

test('heatmap target values', async (t) => {
  await u.createNewReport(t);

  await t.typeText(Common.nameEditField, 'Invoice Pipeline', {replace: true});

  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Flow Node', 'Duration');

  await t.resizeWindow(1650, 850);

  await u.selectVisualization(t, 'Heatmap');

  await t.hover(e.flowNode('approveInvoice'));

  await t.expect(e.tooltip.textContent).notContains('Target duration');

  await t.click(e.targetValueButton);
  await t.typeText(e.targetValueInput('Approve Invoice'), '1');
  await t.typeText(e.targetValueInput('Prepare Bank Transfer'), '5');
  await t.typeText(e.targetValueInput('Review Invoice'), '1');

  await t.takeElementScreenshot(
    Common.modalContainer,
    'process-analysis/report-analysis/img/targetvalue-2.png'
  );

  await t.click(Common.modalConfirmButton);

  await t.hover(e.flowNode('approveInvoice'));

  await t.expect(e.tooltip.textContent).contains('Target duration: 1\u00A0hour');

  await addAnnotation(e.targetValueButton, 'Toggle Target Value Mode');
  await addAnnotation(e.tooltip, 'Target Value Tooltip', {x: -50, y: 0});
  await addAnnotation(
    e.flowNode('prepareBankTransfer'),
    'Activity with Duration above\nTarget Value',
    {x: -50, y: 0}
  );
  await addAnnotation(e.badge('prepareBankTransfer'), 'Target Value for Activity', {x: 50, y: 0});

  await t.takeScreenshot('process-analysis/report-analysis/img/targetvalue-1.png', {
    fullPage: true,
  });

  await clearAllAnnotations();

  await t.click(e.targetValueButton);

  await t.hover(e.flowNode('approveInvoice'));

  await t.expect(e.tooltip.textContent).notContains('target duration');

  await t.maximizeWindow();
});

test('always show tooltips', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Flow Node', 'Count');

  await t.resizeWindow(1650, 850);

  await u.selectVisualization(t, 'Heatmap');

  await t.expect(e.tooltip.exists).notOk();

  await t.click(e.configurationButton);
  await t.click(e.selectSwitchLabel('Show Absolute Value'));
  await t.click(e.selectSwitchLabel('Show Relative Value'));

  await t
    .takeScreenshot('process-analysis/report-analysis/img/heatmap.png', {fullPage: true})
    .maximizeWindow();

  await t.expect(e.tooltip.visible).ok();
});

test('should only enable valid combinations for user task', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await u.selectView(t, 'User Task', 'Count');

  await t.click(e.groupbyDropdown);

  await t.expect(Common.option('Flow Nodes').hasClass('disabled')).ok();
  await t.expect(Common.option('User Task').hasClass('disabled')).notOk();
  await t.expect(Common.option('Assignee').hasClass('disabled')).notOk();
  await t.expect(Common.option('Candidate Group').hasClass('disabled')).notOk();
  await t.expect(Common.option('Start Date').hasClass('disabled')).notOk();

  await t.click(Common.option('User Tasks'));

  await t.click(e.visualizationDropdown);

  await t.expect(Common.option('Number').hasClass('disabled')).ok();
  await t.expect(Common.option('Table').hasClass('disabled')).notOk();
  await t.expect(Common.option('Bar Chart').hasClass('disabled')).notOk();
  await t.expect(Common.option('Heatmap').hasClass('disabled')).notOk();

  await u.selectGroupby(t, 'Assignee');

  await t.click(e.visualizationDropdown);

  await t.expect(Common.option('Heatmap').hasClass('disabled')).ok();

  await t.click(Common.option('Table'));

  await t.expect(e.reportTable.visible).ok();
});

test('should be able to distribute candidate group by user task', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Lead Qualification');
  await u.selectView(t, 'User Task', 'Count');

  await u.selectGroupby(t, 'Candidate Group');

  await u.selectVisualization(t, 'Pie Chart');

  await t.click(e.distributedBySelect);

  await t.click(e.dropdownOption('User Task'));

  await t.expect(e.visualizationDropdown.textContent).contains('Bar Chart');

  await t.takeElementScreenshot(
    e.reportRenderer,
    'process-analysis/report-analysis/img/distributed-report.png'
  );

  await t.click(e.visualizationDropdown);

  await t.expect(Common.option('Table').hasClass('disabled')).notOk();
  await t.expect(Common.option('Bar Chart').hasClass('disabled')).notOk();
  await t.expect(Common.option('Line Chart').hasClass('disabled')).notOk();
  await t.expect(Common.option('Number').hasClass('disabled')).ok();
  await t.expect(Common.option('Pie Chart').hasClass('disabled')).ok();

  await t.click(Common.option('Table'));
  await t.expect(e.reportTable.visible).ok();
});

test('should be able to select how the time of the user task is calculated', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'User Task', 'Duration');
  await u.selectGroupby(t, 'Candidate Group');
  await u.selectVisualization(t, 'Table');

  await t.click(e.aggregationTypeSelect);
  await t.click(e.aggregationOption('Idle'));
  await t.click(e.aggregationOption('Total'));

  await t.expect(e.reportTable.visible).ok();

  await t.click(e.aggregationOption('Work'));
  await t.click(e.aggregationOption('Idle'));

  await t.expect(e.reportTable.visible).ok();
});

test('show process instance count', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Raw Data');

  await t.click(e.configurationButton);
  await t.click(e.instanceCountSwitch);

  await t.expect(e.instanceCount.visible).ok();
  await t.expect(e.instanceCount.textContent).contains('Total Instance Count:');
});

test('process parts', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await u.selectView(t, 'Process Instance', 'Duration');

  const withoutPart = await e.reportNumber.textContent;

  await t.resizeWindow(1150, 700);

  await t.click(e.processPartButton);
  await t.click(e.modalFlowNode('StartEvent_1'));
  await t.click(e.modalFlowNode('assignApprover'));

  await t.takeElementScreenshot(
    Common.modalContainer,
    'process-analysis/report-analysis/img/process-part.png'
  );
  await t.maximizeWindow();

  await t.click(Common.modalConfirmButton);

  const withPart = await e.reportNumber.textContent;

  await t.expect(withoutPart).notEql(withPart);
});

test('deleting', async (t) => {
  await u.createNewReport(t);

  await u.save(t);
  await t.click(Common.deleteButton);
  await t.click(Common.modalConfirmButton);

  await t.expect(e.report.exists).notOk();
});

test('show raw data and process model', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Duration');
  await u.save(t);

  await t.click(e.detailsPopoverButton);
  await t.click(e.modalButton('View Raw data'));
  await t.expect(e.rawDataTable.visible).ok();
  await t.click(e.rawDataModalCloseButton);

  await t.click(e.detailsPopoverButton);
  await t.click(e.modalButton('View Process Model'));
  await t.expect(e.modalDiagram.visible).ok();
});

test('group by duration', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
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
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'Start Date', 'Automatic');
  await u.selectVisualization(t, 'Bar Chart');

  await t.click(e.distributedBySelect);
  await t.click(e.dropdownOption('Variable'));
  await t.click(Common.submenuOption('approved'));
  await t
    .resizeWindow(1650, 900)
    .takeElementScreenshot(
      e.reportRenderer,
      'process-analysis/report-analysis/img/distributedByVar.png'
    )
    .maximizeWindow();

  await t.click(e.distributedBySelect);
  await t.click(e.dropdownOption('Variable'));
  await t.click(Common.submenuOption('invoiceCategory'));
  await u.selectVisualization(t, 'Table');

  await t.expect(e.reportRenderer.textContent).contains('Misc');

  await u.selectView(t, 'Flow Node', 'Count');

  await t.expect(e.reportRenderer.textContent).notContains('Misc');
});

test('distribute by start/end date', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'Variable', 'invoiceCategory');
  await u.selectVisualization(t, 'Bar Chart');
  await t.click(e.distributedBySelect);
  await t.click(e.dropdownOption('Start Date'));
  await t.click(Common.submenuOption('Month'));
  await t.click(e.distributedBySelect);
  await t.click(e.dropdownOption('End Date'));
  await t.click(Common.submenuOption('Automatic'));
  await u.selectGroupby(t, 'Variable', 'boolVar');

  await t.expect(e.reportChart.visible).ok();
});

test('incident reports', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Incident Process');
  await u.selectView(t, 'Incident', 'Count');
  await t.click(e.removeGroupButton);

  await t.expect(e.reportNumber.visible).ok();

  await u.selectView(t, 'Incident', 'Resolution Duration');

  await t.expect(e.reportNumber.visible).ok();

  await u.selectGroupby(t, 'Flow Nodes');
  await u.selectVisualization(t, 'Bar Chart');

  await t.expect(e.reportChart.visible).ok();

  await u.selectVisualization(t, 'Table');

  await t.expect(e.reportRenderer.textContent).contains('Resolution Duration');
});

test('multi-measure reports', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Hiring Demo 5 Tenants');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(e.addMeasureButton);
  await t.click(e.dropdownOption('Percentage'));
  await t.expect(e.reportNumber.visible).ok();
  await t.expect(e.reportRenderer.textContent).contains('Process Instance Count');
  await t.expect(e.reportRenderer.textContent).contains('% of total instances');
  await t.click(e.removeMeasureButton);

  await t.click(e.addMeasureButton);
  await t.click(e.dropdownOption('Duration'));

  await t.expect(e.reportNumber.visible).ok();
  await t.expect(e.reportRenderer.textContent).contains('Process Instance Count');
  await t.expect(e.reportRenderer.textContent).contains('Process Instance Duration');

  await u.selectGroupby(t, 'Start Date', 'Automatic');
  await u.selectVisualization(t, 'Table');

  await t.expect(e.reportRenderer.textContent).contains('Count');
  await t.expect(e.reportRenderer.textContent).contains('Duration');

  await u.selectVisualization(t, 'Bar Chart');
  await t.expect(e.reportChart.visible).ok();
  await u.selectVisualization(t, 'Line Chart');
  await t.expect(e.reportChart.visible).ok();
  await u.selectVisualization(t, 'Pie Chart');
  await t.expect(e.reportChart.visible).ok();

  await u.selectView(t, 'Flow Node');
  await u.selectGroupby(t, 'Flow Nodes');
  await u.selectVisualization(t, 'Heatmap');

  await t.expect(e.reportDiagram.visible).ok();

  await t.click(e.heatDropdown);
  await t.click(Common.option('Heat: Duration - Avg'));

  await t.expect(e.reportDiagram.visible).ok();
});

test('multi-aggregation reports', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Hiring Demo 5 Tenants');
  await u.selectView(t, 'Process Instance', 'Duration');

  await t.click(e.aggregationTypeSelect);
  await t.click(e.aggregationOption('Maximum'));

  await t.expect(e.reportNumber.visible).ok();
  await t.expect(e.reportRenderer.textContent).contains('Avg');
  await t.expect(e.reportRenderer.textContent).contains('Max');

  await u.selectView(t, 'User Task', 'Duration');
  await t.click(e.aggregationTypeSelect);
  await t.click(e.aggregationOption('Work'));
  await u.selectVisualization(t, 'Table');

  await t.expect(e.reportRenderer.textContent).contains('Total Duration - Avg');
  await t.expect(e.reportRenderer.textContent).contains('Total Duration - Max');
  await t.expect(e.reportRenderer.textContent).contains('Work Duration - Avg');
  await t.expect(e.reportRenderer.textContent).contains('Work Duration - Max');

  await u.selectVisualization(t, 'Bar Chart');
  await t.expect(e.reportChart.visible).ok();
  await u.selectVisualization(t, 'Line Chart');
  await t.expect(e.reportChart.visible).ok();
  await u.selectVisualization(t, 'Pie Chart');
  await t.expect(e.reportChart.visible).ok();
  await u.selectVisualization(t, 'Heatmap');
  await t.expect(e.reportDiagram.visible).ok();

  await t.hover(e.flowNode('ConductPhoneInterview'));
  await t.expect(e.tooltip.textContent).contains('Avg (Total)');
  await t.expect(e.tooltip.textContent).contains('Max (Total)');
  await t.expect(e.tooltip.textContent).contains('Avg (Work)');
  await t.expect(e.tooltip.textContent).contains('Max (Work)');
});

test('distributed multi-measure reports', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await u.selectView(t, 'Process Instance', 'Duration');
  await u.selectGroupby(t, 'Start Date', 'Automatic');
  await u.selectVisualization(t, 'Bar Chart');

  await t.click(e.distributedBySelect);
  await t.click(e.dropdownOption('Variable'));
  await t.click(Common.submenuOption('invoiceCategory'));

  await t.click(e.addMeasureButton);
  await t.click(e.dropdownOption('Count'));

  await t.expect(e.reportChart.visible).ok();

  await u.selectVisualization(t, 'Table');

  await t.expect(e.reportRenderer.textContent).contains('Count');
  await t.expect(e.reportRenderer.textContent).contains('Duration');
  await t.expect(e.reportRenderer.textContent).contains('Misc');
});

test('multi-definition report', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await u.selectView(t, 'Process Instance', 'Count');

  const singleDefinitionInstances = +(await e.reportNumber.textContent);

  await u.selectReportDefinition(t, 'Hiring Demo 5 Tenants');

  const multiDefinitionInstances = +(await e.reportNumber.textContent);

  await t.expect(multiDefinitionInstances > singleDefinitionInstances).ok();

  await t.click(e.addDefinitionButton);
  await t.click(e.definitionEntry('Book Request One Tenant'));
  await t.click(e.definitionEntry('Book Request with no business key'));

  await t.resizeWindow(1650, 700);

  await t.takeElementScreenshot(
    Common.modalContainer,
    'process-analysis/report-analysis/img/report-processDefinitionSelection.png'
  );

  await t.maximizeWindow();
});

test('group by process', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectReportDefinition(t, 'Hiring Demo 5 Tenants');

  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'Process');

  await t.expect(e.reportChart.visible).ok();

  await t.click(e.addMeasureButton);
  await t.expect(e.reportChart.visible).ok();

  await u.selectView(t, 'Flow Node');
  await t.expect(e.distributedBySelect.textContent).contains('Process');
  await t.expect(e.reportChart.visible).ok();

  await u.selectView(t, 'User Task');
  await t.expect(e.distributedBySelect.textContent).contains('Process');
  await t.expect(e.reportChart.visible).ok();
});

test('variable renaming', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await u.selectView(t, 'Variable', 'amount');

  await t.click(e.definitionEditor);
  await t.click(e.renameVariablesBtn);
  await t.typeText(e.newNameInput('amount'), 'renamed amount', {replace: true});
  await t.click(Common.modalConfirmButton);
  await t.click(e.definitionEditor);

  await t.expect(e.viewSelect.textContent).contains('renamed amount');
  await t.expect(e.numberReportInfo.textContent).contains('renamed amount');

  // remove the added label since the label changes are global
  // and may affect other tests
  await t.click(e.definitionEditor);
  await t.click(e.renameVariablesBtn);
  await t.selectText(e.newNameInput('amount')).pressKey('delete');
  await t.click(Common.modalConfirmButton);
  await t.click(e.definitionEditor);

  await t.expect(e.viewSelect.textContent).contains('amount');
});

test('create report with two versions of the same process', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  const definition1 = e
    .definitionElement('Invoice Receipt with alternative correlation variable')
    .nth(0);

  await t.click(e.definitionCopyButton(definition1));

  await t
    .expect(e.definitionElement('Invoice Receipt with alternative correlation variable').count)
    .eql(2);

  const definition2 = e
    .definitionElement('Invoice Receipt with alternative correlation variable')
    .nth(1);

  await u.selectVersion(t, definition1, [5]);
  await u.selectVersion(t, definition2, [4]);

  await t.click(e.definitionEditButton(definition1));
  await t.click(e.versionPopover);

  await t.takeElementScreenshot(
    '.DefinitionEditor',
    'additional-features/img/process-version-selection.png'
  );

  await t.click(e.definitionEditButton(definition1));

  await t.expect(e.definitionElement('Version: 5').exists).ok();
  await t.expect(e.definitionElement('Version: 4').exists).ok();

  await u.selectView(t, 'Process Instance', 'Duration');
  await u.selectGroupby(t, 'Process');

  await t.click(e.configurationButton);
  await t.click(e.selectSwitchLabel('Show Absolute Value'));
  await t.click(e.configurationButton);

  await t
    .resizeWindow(1600, 800)
    .takeScreenshot('additional-features/img/report-with-process-variants.png', {
      fullPage: true,
    });
});

test('Display precision properly', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await t.typeText(Common.nameEditField, 'Precision Report', {replace: true});

  await u.selectView(t, 'Flow Node', 'Duration');
  await t.click(e.addMeasureButton);
  await t.click(e.dropdownOption('Count'));
  await u.selectVisualization(t, 'Table');

  await u.selectGroupby(t, 'Start Date', 'Automatic');

  let a = e.tableCell(19, 1);
  let b = e.tableCell(19, 2);
  let c = e.tableCell(19, 3);

  // Default precision for duration is 3
  // shouldn't affect percentage values
  await t.expect(a.textContent).match(/\d+/);
  await t.expect(b.textContent).match(/\d(\.\d)?%/);
  await t.expect(c.textContent).match(/\d+.[a-zA-Z]*.\d+.[a-zA-Z]*.\d+.[a-zA-Z]*/);

  await t.click(e.configurationButton);
  await t.click(e.limitPrecisionSwitch);
  await t.typeText(e.limitPrecisionInput, '4', {replace: true});

  a = e.tableCell(19, 1);
  b = e.tableCell(19, 2);
  c = e.tableCell(19, 3);

  await t.expect(a.textContent).match(/\d+/);
  await t.expect(b.textContent).match(/\d(\.\d)?%/);
  await t.expect(c.textContent).match(/\d+.[a-zA-Z]*.\d+.[a-zA-Z]*.\d+.[a-zA-Z]*.\d+.[a-zA-Z]*/);

  await t.typeText(e.limitPrecisionInput, '1', {replace: true});

  a = e.tableCell(19, 1);
  b = e.tableCell(19, 2);
  c = e.tableCell(19, 3);

  await t.expect(a.textContent).match(/\d+/);
  await t.expect(b.textContent).match(/\d(\.\d)?%/);
  await t.expect(c.textContent).match(/\d+.[a-zA-Z]*/);
});

test('add, edit and remove reports description', async (t) => {
  await u.createNewReport(t);

  // Add description
  const description = 'This is a description of the report.';
  await u.addEditEntityDescription(t, description);

  await t.expect(Common.descriptionField.textContent).contains(description);

  await u.save(t);
  await u.gotoOverview(t);
  await t.expect(Common.reportItem.textContent).contains(description);

  await t.click(Common.reportItem);
  await t.expect(Common.descriptionField.textContent).contains(description);

  // Edit description
  await t.resizeWindow(1200, 600);
  await t.click(Common.editButton);
  const newDescription =
    'This is a new description of the report. This time the description is very long and it will not fit in one line. It will display ellipsis and More button.';
  await u.addEditEntityDescription(t, newDescription);

  await t.expect(Common.descriptionField.textContent).contains(newDescription);

  await u.save(t);

  // Toggle show less/more
  await t.expect(Common.descriptionField.find('p').hasClass('overflowHidden')).ok();
  await t.expect(Common.showLessMoreDescriptionButton.textContent).contains('More');

  await t.click(Common.showLessMoreDescriptionButton);

  await t.expect(Common.descriptionField.find('p').hasClass('overflowHidden')).notOk();
  await t.expect(Common.showLessMoreDescriptionButton.textContent).contains('Less');

  await t.click(Common.showLessMoreDescriptionButton);
  await t.expect(Common.descriptionField.find('p').hasClass('overflowHidden')).ok();
  await t.expect(Common.showLessMoreDescriptionButton.textContent).contains('More');

  // Remove description
  await t.click(Common.editButton);
  await u.addEditEntityDescription(t);
  await t.expect(Common.descriptionField.textContent).contains('No description yet');

  await u.save(t);

  await t.expect(Common.descriptionField.exists).notOk();
});
