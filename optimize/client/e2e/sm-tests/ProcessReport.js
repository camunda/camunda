/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';
// import {addAnnotation, clearAllAnnotations} from '../browserMagic';

import * as e from './ProcessReport.elements.js';
import * as Common from './Common.elements.js';

fixture('Process report')
  .page(config.endpoint)
  .beforeEach(async (t) => {
    await u.login(t);
    await t.navigateTo(config.collectionsEndpoint);
  })
  .afterEach(cleanEntities);

test('create a report from a template', async (t) => {
  await t.resizeWindow(1300, 750);
  await t.click(Common.createNewButton);
  await t.click(Common.menuOption('Report'));

  await t.click(Common.templateModalProcessField);
  await t.click(Common.carbonOption('Order process'));

  await t.click(Common.templateOption('Analyze shares as pie chart'));

  await t.takeScreenshot('img/reportTemplate.png', {fullPage: true});
  await t.maximizeWindow();

  await t.click(Common.modalConfirmButton);

  await t.expect(Common.nameEditField.value).eql('Analyze shares as pie chart');
  await t.expect(e.groupbyDropdownButton.textContent).contains('Start date : Year');
  await t.expect(e.reportChart.visible).ok();
});

test('create and name a report', async (t) => {
  await u.createNewReport(t);

  await t.typeText(Common.nameEditField, 'Invoice Pipeline', {replace: true});

  await u.addEditEntityDescription(t, 'This is a description of the dashboard.');

  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'Flow node', 'Count');

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

  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'Flow node', 'Count');

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

// test('version selection', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'complexProcess');

//   await u.selectView(t, 'Process instance', 'Count');

//   await t.click(e.definitionEditor);
//   await t.click(e.versionPopover);
//   await t.click(e.versionAll);

//   const allNumber = +(await e.reportNumber.textContent);

//   await t.click(e.versionLatest);

//   const latestNumber = +(await e.reportNumber.textContent);

//   await t.click(e.versionSpecific);
//   await t.click(e.versionCheckbox(0));
//   await t.click(e.versionCheckbox(1));
//   await t.click(e.versionCheckbox(2));

//   await t.takeElementScreenshot(
//     e.definitionEditorDialog,
//     'process-analysis/report-analysis/img/report-versionSelection.png'
//   );

//   const rangeNumber = +(await e.reportNumber.textContent);

//   await t.expect(allNumber > rangeNumber).ok();
//   await t.expect(rangeNumber > latestNumber).ok();

//   await t.click(e.tenantPopover);

//   await t.takeElementScreenshot(
//     e.definitionEditorDialog,
//     'process-analysis/report-analysis/img/tenantSelection.png'
//   );
// });

test('raw data table pagination', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'Raw data');
  await t.click(e.nextPageButton);
  await t.click(e.rowsPerPageButton);
  await t.click(e.rowsPerPageOption('100'));
  await t.expect(e.reportTable.visible).ok();
});

// test('sort table columns', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');
//   await u.selectView(t, 'Raw data');
//   await t.resizeWindow(1600, 750);

//   await t.click(e.configurationButton);
//   await t.click(e.selectSectionWithLabel('VARIABLES'));
//   await t.click(Common.toggleElement('invoice'));
//   await t.click(e.configurationButton);

//   await t.typeText(Common.nameEditField, 'Table Report', {replace: true});

//   await t.expect(e.reportRenderer.textContent).contains('invoice');
//   await t.expect(e.reportRenderer.textContent).contains('Start date');

//   await t.click(e.tableHeader(9));

//   await t
//     .resizeWindow(1600, 650)
//     .takeScreenshot('process-analysis/report-analysis/img/sorting.png', {fullPage: true})
//     .maximizeWindow();

//   let a, b, c;

//   a = await e.tableCell(0, 9);
//   b = await e.tableCell(1, 9);
//   c = await e.tableCell(2, 9);

//   await t.expect(a <= b).ok();
//   await t.expect(b <= c).ok();

//   await t.click(e.tableHeader(9));

//   a = await e.tableCell(0, 9);
//   b = await e.tableCell(1, 9);
//   c = await e.tableCell(2, 9);

//   await t.expect(a >= b).ok();
//   await t.expect(b >= c).ok();
// });

test('drag raw data table columns', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'Raw data');

  const originalPositionText = await e.tableHeader(3).textContent;
  await t.drag(e.tableHeader(3), 350, 60);
  const newPositionText = await e.tableHeader(4).textContent;
  await t.expect(originalPositionText).eql(newPositionText);
});

// test('view a variable object in rawdata table', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');
//   await u.selectView(t, 'Raw data');
//   await t.resizeWindow(1600, 750);

//   await t.click(e.configurationButton);
//   await t.click(e.selectSectionWithLabel('VARIABLES'));
//   await t.click(Common.toggleElement('person'));
//   await t.click(e.configurationButton);

//   await t.scrollIntoView(e.objectViewBtn);

//   await t.click(e.objectViewBtn);
//   await t.expect(e.objectVariableModal.visible).ok();

//   await t.click(e.objectVariableModalCloseButton);
// });

// test('drag distributed table columns', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Big variable process');
//   await u.selectView(t, 'User task', 'Count');
//   await u.selectGroupby(t, 'Assignee');
//   await u.selectVisualization(t, 'Table');

//   const originalPositionText = await e.tableGroup(1).textContent;
//   await t.drag(e.tableHeader(1), 600, 0);
//   const newPositionText = await e.tableGroup(2).textContent;
//   await t.expect(originalPositionText).eql(newPositionText);
// });

// test('exclude raw data columns', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');
//   await u.selectView(t, 'Raw data');

//   await t.resizeWindow(1600, 750);

//   await t.click(e.configurationButton);

//   await t.click(Common.toggleElement('Start date'));
//   await t.click(e.instanceCountSwitch);
//   await t.click(Common.toggleElement('Process definition key'));
//   await t.click(Common.toggleElement('Business key'));
//   await t.click(Common.toggleElement('End date'));

//   await t.click(e.selectSectionWithLabel('VARIABLES'));

//   await t.click(Common.toggleElement('approved'));

//   await t.takeScreenshot('process-analysis/report-analysis/img/rawdata.png').maximizeWindow();

//   await t.expect(e.reportRenderer.textContent).notContains('Start date');
// });

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
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'Process instance', 'Count');

  await t.click(e.groupbyDropdown);

  await t.expect(Common.menuOption('Start date').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Variable').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Flow nodes').getAttribute('aria-disabled')).eql('true');

  await t.click(e.visualizationDropdown);

  await t.expect(Common.menuOption('Number').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Table').getAttribute('aria-disabled')).eql('true');
  await t.expect(Common.menuOption('Bar chart').getAttribute('aria-disabled')).eql('true');
  await t.expect(Common.menuOption('Heatmap').getAttribute('aria-disabled')).eql('true');
  await t.expect(Common.menuOption('Bar/line chart').getAttribute('aria-disabled')).eql('true');

  await t.expect(e.reportNumber.visible).ok();
});

test('Limit the precision in number report', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');

  await t.typeText(Common.nameEditField, 'Number Report', {replace: true});

  await u.selectView(t, 'Process instance', 'Duration');

  await t.click(e.configurationButton);
  await t.click(e.limitPrecisionSwitch);
  await t.typeText(e.limitPrecisionInput, '2', {replace: true});

  await t
    .resizeWindow(1600, 850)
    .takeScreenshot('process-analysis/report-analysis/img/NumberConfiguration.png', {
      fullPage: true,
    })
    .maximizeWindow();

  await t.expect(e.reportNumber.visible).ok();
});

test('Limit the precision in chart type reports', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');

  await t.typeText(Common.nameEditField, 'Chart Report', {replace: true});

  await u.selectView(t, 'Flow node', 'Duration');
  await u.selectVisualization(t, 'Bar chart');

  await t.click(e.configurationButton);
  await t.click(e.limitPrecisionSwitch);
  await t.typeText(e.limitPrecisionInput, '2', {replace: true});
  await t.expect(e.reportChart.visible).ok();

  // Heatmap
  await u.selectVisualization(t, 'Heatmap');
  await t.click(e.configurationButton);
  await t.expect(e.limitPrecisionInput.visible).ok();
  await t.expect(e.reportDiagram.visible).ok();
});

test('Disable absolute and relative values for table reports', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');

  await u.selectView(t, 'Process instance', 'Count');
  await u.selectGroupby(t, 'Start date', 'Month');

  await u.selectVisualization(t, 'Table');
  await t.click(e.configurationButton);
  await t.click(Common.toggleElement('Show absolute value'));
  await t.click(Common.toggleElement('Show relative value'));

  await t.expect(e.reportTable.textContent).contains('Start date');
  await t.expect(e.reportTable.textContent).notContains('Process Instance: Count');
  await t.expect(e.reportTable.textContent).notContains('Relative Frequency');
});

test('select process instance count grouped by end date', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'Process instance', 'Count');

  await u.selectGroupby(t, 'End date', 'Automatic');

  await t.click(e.visualizationDropdown);

  await t.expect(Common.menuOption('Number').getAttribute('aria-disabled')).eql('true');
  await t.expect(Common.menuOption('Table').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Bar chart').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Line chart').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Pie chart').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Heatmap').getAttribute('aria-disabled')).eql('true');
  await t.expect(Common.menuOption('Bar/line chart').getAttribute('aria-disabled')).eql('true');

  await t.click(e.visualizationDropdown);

  await u.selectVisualization(t, 'Table');
  await t.expect(e.reportTable.visible).ok();
});

// test('select process instance count grouped by variable', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');

//   await u.selectView(t, 'Process instance', 'Count');

//   await u.selectGroupby(t, 'Variable', 'amount');

//   await t.click(e.visualizationDropdown);

//   await t.expect(Common.menuOption('Number').getAttribute('aria-disabled')).eql('true');
//   await t.expect(Common.menuOption('Table').getAttribute('aria-disabled')).eql('false');
//   await t.expect(Common.menuOption('Bar chart').getAttribute('aria-disabled')).eql('false');
//   await t.expect(Common.menuOption('Line chart').getAttribute('aria-disabled')).eql('false');
//   await t.expect(Common.menuOption('Pie chart').getAttribute('aria-disabled')).eql('false');
//   await t.expect(Common.menuOption('Heatmap').getAttribute('aria-disabled')).eql('true');
//   await t.expect(Common.menuOption('Bar/line chart').getAttribute('aria-disabled')).eql('true');

//   await t.click(e.visualizationDropdown);

//   await u.selectVisualization(t, 'Table');
//   await t.expect(e.reportTable.textContent).contains('Process instance Var: amount');
// });

// test('variable report', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');

//   await u.selectView(t, 'Variable', 'amount');

//   await t.expect(e.reportNumber.visible).ok();

//   await t.click(e.configurationButton);
//   await t.click(e.limitPrecisionSwitch);
//   await t.typeText(e.limitPrecisionInput, '2', {replace: true});

//   await t.expect(e.reportNumber.visible).ok();
// });

test('should only enable valid combinations for Flow Node Count', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');

  await u.selectView(t, 'Flow node', 'Count');

  await t.click(e.visualizationDropdown);

  await t.expect(Common.menuOption('Number').getAttribute('aria-disabled')).eql('true');
  await t.expect(Common.menuOption('Table').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Bar chart').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Heatmap').getAttribute('aria-disabled')).eql('false');
});

// test('bar chart and line chart configuration', async (t) => {
//   await u.createNewReport(t);
//   await t.typeText(Common.nameEditField, 'Bar chart Report', {replace: true});

//   await u.selectReportDefinition(t, 'Order process');

//   await u.selectView(t, 'Process instance', 'Count');
//   await u.selectGroupby(t, 'Start date', 'Automatic');
//   await u.selectVisualization(t, 'Bar chart');

//   await t.resizeWindow(1600, 800);

//   await t.click(e.configurationButton);
//   await t.click(e.cyanColor);

//   await t.takeScreenshot('process-analysis/report-analysis/img/chartConfiguration.png', {
//     fullPage: true,
//   });

//   await t.click(Common.toggleElement('Set target'));

//   await t.typeText(e.goalTargetInput, '4.5', {replace: true});
//   await t.expect(e.goalTargetInput.hasAttribute('disabled')).notOk();

//   await t.expect(e.reportChart.visible).ok();

//   await t.takeScreenshot('process-analysis/report-analysis/img/targetValue.png', {fullPage: true});

//   await u.selectVisualization(t, 'Line chart');

//   await t.takeElementScreenshot(
//     e.reportRenderer,
//     'process-analysis/report-analysis/img/targetline.png'
//   );

//   await t.maximizeWindow();

//   await t.click(e.configurationButton);

//   await t.click(Common.toggleElement('Logarithmic scale'));

//   await t.typeText(e.axisInputs('X-axis label'), 'x axis label', {replace: true});
//   await t.typeText(e.axisInputs('Y-axis label'), 'y axis label', {replace: true});

//   await t.click(Common.toggleElement('Logarithmic scale'));

//   await t.expect(e.reportChart.visible).ok();

//   await t.click(e.configurationButton);

//   await t.click(e.distributedBySelect);
//   await t.hover(Common.menuOption('Variable'));
//   await t.click(Common.submenuOption('boolVar'));
//   await u.selectVisualization(t, 'Bar chart');

//   await t.click(e.configurationButton);

//   await t.click(Common.toggleElement('Stacked bars'));

//   await t
//     .resizeWindow(1600, 800)
//     .takeScreenshot('process-analysis/report-analysis/img/stackedBar.png', {fullPage: true});

//   await t.click(e.configurationButton);

//   await t.click(e.addMeasureButton);
//   await t.click(Common.menuOption('Duration'));
//   await u.selectVisualization(t, 'Bar/line chart');

//   await t.click(e.configurationButton);

//   await t.takeScreenshot('process-analysis/report-analysis/img/barLine.png', {fullPage: true});

//   await t.click(e.lineButton);

//   await t.expect(e.reportChart.visible).ok();

//   await t.maximizeWindow();
// });

test('horizontal bar chart', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'User task', 'Duration');
  await u.selectVisualization(t, 'Bar chart');

  await t.resizeWindow(1600, 800);

  await t.click(e.configurationButton);

  await t.takeScreenshot('process-analysis/report-analysis/img/horizontalBar.png', {
    fullPage: true,
  });

  await t.click(Common.toggleElement('Horizontal bars'));

  await t.maximizeWindow();
});

test('different visualizations', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'Flow node', 'Duration');
  await u.selectVisualization(t, 'Table');

  await t.expect(e.reportTable.visible).ok();

  await u.selectVisualization(t, 'Bar chart');

  await t.expect(e.reportTable.exists).notOk();
  await t.expect(e.reportChart.visible).ok();

  await u.selectVisualization(t, 'Heatmap');

  await t.expect(e.reportChart.exists).notOk();
  await t.expect(e.reportDiagram.visible).ok();

  await u.selectView(t, 'Process instance', 'Duration');

  await t.expect(e.reportDiagram.exists).notOk();
  await t.expect(e.reportNumber.visible).ok();
});

test('aggregators', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'Process instance', 'Duration');

  await t.click(e.sectionToggle('Filters'));
  await t.click(e.filterButton);
  await t.click(Common.menuOption('Instance state'));
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
  await u.selectReportDefinition(t, 'Order process');

  await u.selectView(t, 'Process instance', 'Count');

  await t.click(e.configurationButton);

  await t.click(Common.toggleElement('Set target'));
  await t.typeText(e.goalTargetInput, '400', {replace: true});

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
  await t.typeText(e.goalTargetInput, '300', {replace: true});
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

// test('heatmap target values', async (t) => {
//   await u.createNewReport(t);

//   await t.typeText(Common.nameEditField, 'Invoice Pipeline', {replace: true});

//   await u.selectReportDefinition(t, 'Order process');
//   await u.selectView(t, 'Flow node', 'Duration');

//   await t.resizeWindow(1650, 850);

//   await u.selectVisualization(t, 'Heatmap');

//   await t.hover(e.flowNode('approveInvoice'));

//   await t.expect(e.tooltip.textContent).notContains('Target duration');

//   await t.click(e.targetValueButton);
//   await t.typeText(e.targetValueInput('Approve Invoice'), '1');
//   await t.typeText(e.targetValueInput('Prepare Bank Transfer'), '5');
//   await t.typeText(e.targetValueInput('Review Invoice'), '1');

//   await t.takeElementScreenshot(
//     Common.modalContainer,
//     'process-analysis/report-analysis/img/targetvalue-2.png'
//   );

//   await t.click(Common.modalConfirmButton);

//   await t.hover(e.flowNode('approveInvoice'));

//   await t.expect(e.tooltip.textContent).contains('Target duration: 1\u00A0hour');

//   await addAnnotation(e.targetValueButton, 'Toggle Target Value Mode');
//   await addAnnotation(e.tooltip, 'Target Value Tooltip', {x: -50, y: 0});
//   await addAnnotation(
//     e.flowNode('prepareBankTransfer'),
//     'Activity with Duration above\nTarget Value',
//     {x: -50, y: 0}
//   );
//   await addAnnotation(e.badge('prepareBankTransfer'), 'Target Value for Activity', {x: 50, y: 0});

//   await t.takeScreenshot('process-analysis/report-analysis/img/targetvalue-1.png', {
//     fullPage: true,
//   });

//   await clearAllAnnotations();

//   await t.click(e.targetValueButton);

//   await t.hover(e.flowNode('approveInvoice'));

//   await t.expect(e.tooltip.textContent).notContains('target duration');

//   await t.maximizeWindow();
// });

test('always show tooltips', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'Flow node', 'Count');

  await t.resizeWindow(1650, 850);

  await u.selectVisualization(t, 'Heatmap');

  await t.expect(e.tooltip.exists).notOk();

  await t.click(e.configurationButton);
  await t.click(Common.toggleElement('Show absolute value'));
  await t.click(Common.toggleElement('Show relative value'));

  await t
    .takeScreenshot('process-analysis/report-analysis/img/heatmap.png', {fullPage: true})
    .maximizeWindow();

  await t.expect(e.tooltip.visible).ok();
});

test('should only enable valid combinations for user task', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');

  await u.selectView(t, 'User task', 'Count');

  await t.click(e.groupbyDropdown);

  await t.expect(Common.menuOption('Flow nodes').getAttribute('aria-disabled')).eql('true');
  await t.expect(Common.menuOption('User task').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Assignee').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Start date').getAttribute('aria-disabled')).eql('false');

  await t.click(Common.menuOption('User tasks'));

  await t.click(e.visualizationDropdown);

  await t.expect(Common.menuOption('Number').getAttribute('aria-disabled')).eql('true');
  await t.expect(Common.menuOption('Table').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Bar chart').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Heatmap').getAttribute('aria-disabled')).eql('false');

  await u.selectGroupby(t, 'Assignee');

  await t.click(e.visualizationDropdown);

  await t.expect(Common.menuOption('Heatmap').getAttribute('aria-disabled')).eql('true');

  await t.click(Common.menuOption('Table'));

  await t.expect(e.reportTable.visible).ok();
});

test('should be able to distribute assignee by user task', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'User task', 'Count');

  await u.selectGroupby(t, 'Assignee');

  await u.selectVisualization(t, 'Pie chart');

  await t.click(e.distributedBySelect);

  await t.click(Common.menuOption('User task'));

  await t.expect(e.visualizationDropdown.textContent).contains('Bar chart');

  await t.takeElementScreenshot(
    e.reportRenderer,
    'process-analysis/report-analysis/img/distributed-report.png'
  );

  await t.click(e.visualizationDropdown);

  await t.expect(Common.menuOption('Table').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Bar chart').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Line chart').getAttribute('aria-disabled')).eql('false');
  await t.expect(Common.menuOption('Number').getAttribute('aria-disabled')).eql('true');
  await t.expect(Common.menuOption('Pie chart').getAttribute('aria-disabled')).eql('true');

  await t.click(Common.menuOption('Table'));
  await t.expect(e.reportTable.visible).ok();
});

test('should be able to select how the time of the user task is calculated', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'User task', 'Duration');
  await u.selectGroupby(t, 'Assignee');
  await u.selectVisualization(t, 'Table');

  await t.click(e.aggregationTypeSelect);
  await t.click(e.aggregationOption('Unassigned'));
  await t.click(e.aggregationOption('Total'));

  await t.expect(e.reportTable.visible).ok();

  await t.click(e.aggregationOption('Assigned'));
  await t.click(e.aggregationOption('Unassigned'));

  await t.expect(e.reportTable.visible).ok();
});

test('show process instance count', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'Raw data');

  await t.click(e.configurationButton);
  await t.click(e.instanceCountSwitch);

  await t.expect(e.instanceCount.visible).ok();
  await t.expect(e.instanceCount.textContent).contains('Total instance count:');
});

// test('process parts', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');

//   await u.selectView(t, 'Process instance', 'Duration');

//   const withoutPart = await e.reportNumber.textContent;

//   await t.resizeWindow(1150, 700);

//   await t.click(e.processPartButton);
//   await t.click(e.modalFlowNode('StartEvent_1'));
//   await t.click(e.modalFlowNode('assignApprover'));

//   await t.takeElementScreenshot(
//     Common.modalContainer,
//     'process-analysis/report-analysis/img/process-part.png'
//   );
//   await t.maximizeWindow();

//   await t.click(Common.modalConfirmButton);

//   const withPart = await e.reportNumber.textContent;

//   await t.expect(withoutPart).notEql(withPart);
// });

test('deleting', async (t) => {
  await u.createNewReport(t);

  await u.save(t);
  await t.click(Common.deleteButton);
  await t.click(Common.modalConfirmButton);

  await t.expect(e.report.exists).notOk();
});

test('show raw data and process model', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'Process instance', 'Duration');
  await u.save(t);

  await t.click(e.detailsPopoverButton);
  await t.click(e.modalButton('View raw data'));
  await t.expect(e.rawDataTable.visible).ok();
  await t.click(e.rawDataModalCloseButton);

  await t.click(e.detailsPopoverButton);
  await t.click(e.modalButton('View process model'));
  await t.expect(e.modalDiagram.visible).ok();
});

// test('group by duration', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');
//   await u.selectView(t, 'Process instance', 'Count');
//   await u.selectGroupby(t, 'Duration');
//   await u.selectVisualization(t, 'Bar chart');

//   await t.expect(e.reportChart.visible).ok();

//   await t.click(e.configurationButton);
//   await t.click(e.bucketSizeSwitch);
//   await t.click(e.bucketSizeUnitSelect);
//   await t.click(Common.menuOption('days'));
//   await t.click(e.configurationButton);

//   await t.expect(e.reportChart.visible).ok();

//   await u.selectView(t, 'Flow node', 'Count');

//   await t.expect(e.reportChart.visible).ok();

//   await t.click(e.distributedBySelect);
//   await t.click(Common.menuOption('Flow node'));
//   await u.selectVisualization(t, 'Table');

//   await t.expect(e.reportRenderer.textContent).contains('Invoice\nprocessed');

//   await u.selectView(t, 'User task', 'Count');

//   await t.expect(e.reportRenderer.textContent).notContains('Invoice processed');
//   await t.expect(e.reportRenderer.textContent).contains('User task: Count');
// });

// test('distribute by variable', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');
//   await u.selectView(t, 'Process instance', 'Count');
//   await u.selectGroupby(t, 'Start date', 'Automatic');
//   await u.selectVisualization(t, 'Bar chart');

//   await t.click(e.distributedBySelect);
//   await t.click(Common.menuOption('Variable'));
//   await t.click(Common.submenuOption('approved'));
//   await t
//     .resizeWindow(1650, 900)
//     .takeElementScreenshot(
//       e.reportRenderer,
//       'process-analysis/report-analysis/img/distributedByVar.png'
//     )
//     .maximizeWindow();

//   await t.click(e.distributedBySelect);
//   await t.click(Common.menuOption('Variable'));
//   await t.click(Common.submenuOption('invoiceCategory'));
//   await u.selectVisualization(t, 'Table');

//   await t.expect(e.reportRenderer.textContent).contains('Misc');

//   await u.selectView(t, 'Flow node', 'Count');

//   await t.expect(e.reportRenderer.textContent).notContains('Misc');
// });

// test('distribute by start/end date', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');
//   await u.selectView(t, 'Process instance', 'Count');
//   await u.selectGroupby(t, 'Variable', 'invoiceCategory');
//   await u.selectVisualization(t, 'Bar chart');
//   await t.click(e.distributedBySelect);
//   await t.click(Common.menuOption('Start date'));
//   await t.click(Common.submenuOption('Month'));
//   await t.click(e.distributedBySelect);
//   await t.click(Common.menuOption('End date'));
//   await t.click(Common.submenuOption('Automatic'));
//   await u.selectGroupby(t, 'Variable', 'boolVar');

//   await t.expect(e.reportChart.visible).ok();
// });

test('incident reports', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Only Incidents Process');
  await u.selectView(t, 'Incident', 'Count');
  await t.click(e.removeGroupButton);

  await t.expect(e.reportNumber.visible).ok();

  await u.selectView(t, 'Incident', 'Resolution duration');

  await t.expect(e.reportNumber.visible).ok();

  await u.selectGroupby(t, 'Flow nodes');
  await u.selectVisualization(t, 'Bar chart');

  await t.expect(e.reportChart.visible).ok();

  await u.selectVisualization(t, 'Table');

  await t.expect(e.reportRenderer.textContent).contains('Resolution duration');
});

test('multi-measure reports', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'Process instance', 'Count');

  await t.click(e.addMeasureButton);
  await t.click(Common.menuOption('Percentage'));
  await t.expect(e.reportNumber.visible).ok();
  await t.expect(e.reportRenderer.textContent).contains('Process instance Count');
  await t.expect(e.reportRenderer.textContent).contains('% of total instances');
  await t.click(e.removeMeasureButton);

  await t.click(e.addMeasureButton);
  await t.click(Common.menuOption('Duration'));

  await t.expect(e.reportNumber.visible).ok();
  await t.expect(e.reportRenderer.textContent).contains('Process instance Count');
  await t.expect(e.reportRenderer.textContent).contains('Process instance Duration');

  await u.selectGroupby(t, 'Start date', 'Automatic');
  await u.selectVisualization(t, 'Table');

  await t.expect(e.reportRenderer.textContent).contains('Count');
  await t.expect(e.reportRenderer.textContent).contains('Duration');

  await u.selectVisualization(t, 'Bar chart');
  await t.expect(e.reportChart.visible).ok();
  await u.selectVisualization(t, 'Line chart');
  await t.expect(e.reportChart.visible).ok();
  await u.selectVisualization(t, 'Pie chart');
  await t.expect(e.reportChart.visible).ok();

  await u.selectView(t, 'Flow node');
  await u.selectGroupby(t, 'Flow nodes');
  await u.selectVisualization(t, 'Heatmap');

  await t.expect(e.reportDiagram.visible).ok();

  await t.click(e.heatDropdown);
  await t.click(Common.menuOption('Heat: Duration - Avg'));

  await t.expect(e.reportDiagram.visible).ok();
});

test('multi-aggregation reports', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectView(t, 'Process instance', 'Duration');

  await t.click(e.aggregationTypeSelect);
  await t.click(e.aggregationOption('Maximum'));

  await t.expect(e.reportNumber.visible).ok();
  await t.expect(e.reportRenderer.textContent).contains('Avg');
  await t.expect(e.reportRenderer.textContent).contains('Max');

  await u.selectView(t, 'User task', 'Duration');
  await t.click(e.aggregationTypeSelect);
  await t.click(e.aggregationOption('Assigned'));
  await u.selectVisualization(t, 'Table');

  await t.expect(e.reportRenderer.textContent).contains('Total Duration - Avg');
  await t.expect(e.reportRenderer.textContent).contains('Total Duration - Max');
  await t.expect(e.reportRenderer.textContent).contains('Assigned Duration - Avg');
  await t.expect(e.reportRenderer.textContent).contains('Assigned Duration - Max');

  await u.selectVisualization(t, 'Bar chart');
  await t.expect(e.reportChart.visible).ok();
  await u.selectVisualization(t, 'Line chart');
  await t.expect(e.reportChart.visible).ok();
  await u.selectVisualization(t, 'Pie chart');
  await t.expect(e.reportChart.visible).ok();
  // await u.selectVisualization(t, 'Heatmap');
  // await t.expect(e.reportDiagram.visible).ok();

  // await t.hover(e.flowNode('ConductPhoneInterview'));
  // await t.expect(e.tooltip.textContent).contains('Avg (Total)');
  // await t.expect(e.tooltip.textContent).contains('Max (Total)');
  // await t.expect(e.tooltip.textContent).contains('Avg (Assigned)');
  // await t.expect(e.tooltip.textContent).contains('Max (Assigned)');
});

// test('distributed multi-measure reports', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');

//   await u.selectView(t, 'Process instance', 'Duration');
//   await u.selectGroupby(t, 'Start date', 'Automatic');
//   await u.selectVisualization(t, 'Bar chart');

//   await t.click(e.distributedBySelect);
//   await t.click(Common.menuOption('Variable'));
//   await t.click(Common.submenuOption('invoiceCategory'));

//   await t.click(e.addMeasureButton);
//   await t.click(Common.menuOption('Count'));

//   await t.expect(e.reportChart.visible).ok();

//   await u.selectVisualization(t, 'Table');

//   await t.expect(e.reportRenderer.textContent).contains('Count');
//   await t.expect(e.reportRenderer.textContent).contains('Duration');
//   await t.expect(e.reportRenderer.textContent).contains('Misc');
// });

// test('multi-definition report', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');

//   await u.selectView(t, 'Process instance', 'Count');

//   const singleDefinitionInstances = +(await e.reportNumber.textContent);

//   await u.selectReportDefinition(t, 'Hiring Demo 5 Tenants');

//   const multiDefinitionInstances = +(await e.reportNumber.textContent);

//   await t.expect(multiDefinitionInstances > singleDefinitionInstances).ok();

//   await t.click(e.addDefinitionButton);
//   await t.click(e.definitionEntry('Book Request One Tenant'));
//   await t.click(e.definitionEntry('Book Request with no business key'));

//   await t.resizeWindow(1650, 700);

//   await t.takeElementScreenshot(
//     Common.modalContainer,
//     'process-analysis/report-analysis/img/report-processDefinitionSelection.png'
//   );

//   await t.maximizeWindow();
// });

test('group by process', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');
  await u.selectReportDefinition(t, 'bigProcess');

  await u.selectView(t, 'Process instance', 'Count');
  await u.selectGroupby(t, 'Process');

  await t.expect(e.reportChart.visible).ok();

  await t.click(e.addMeasureButton);
  await t.expect(e.reportChart.visible).ok();

  await u.selectView(t, 'Flow node');
  await t.expect(e.distributedBySelect.textContent).contains('Process');
  await t.expect(e.reportChart.visible).ok();

  await u.selectView(t, 'User task');
  await t.expect(e.distributedBySelect.textContent).contains('Process');
  await t.expect(e.reportChart.visible).ok();
});

// test('variable renaming', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');

//   await u.selectView(t, 'Variable', 'amount');

//   await t.click(e.definitionEditor);
//   await t.click(e.renameVariablesBtn);
//   await t.typeText(e.newNameInput('amount'), 'renamed amount', {replace: true});
//   await t.click(Common.modalConfirmButton);
//   await t.click(e.definitionEditor);

//   await t.expect(e.viewDropdown.textContent).contains('renamed amount');
//   await t.expect(e.numberReportInfo.textContent).contains('renamed amount');

//   // remove the added label since the label changes are global
//   // and may affect other tests
//   await t.click(e.definitionEditor);
//   await t.click(e.renameVariablesBtn);
//   await t.selectText(e.newNameInput('amount')).pressKey('delete');
//   await t.click(Common.modalConfirmButton);
//   await t.click(e.definitionEditor);

//   await t.expect(e.viewDropdown.textContent).contains('amount');
// });

// test('create report with two versions of the same process', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');

//   const definition1 = e.definitionElement('Order process').nth(0);

//   await t.click(e.definitionCopyButton(definition1));

//   await t.expect(e.definitionElement('Order process').count).eql(2);

//   const definition2 = e.definitionElement('Order process').nth(1);

//   await u.selectVersion(t, definition1, [5]);
//   await u.selectVersion(t, definition2, [4]);

//   await t.click(e.definitionEditButton(definition1));
//   await t.click(e.versionPopover);

//   await t.takeElementScreenshot(
//     '.DefinitionEditor',
//     'additional-features/img/process-version-selection.png'
//   );

//   await t.click(e.definitionEditButton(definition1));

//   await t.expect(e.definitionElement('Version: 5').exists).ok();
//   await t.expect(e.definitionElement('Version: 4').exists).ok();

//   await u.selectView(t, 'Process instance', 'Duration');
//   await u.selectGroupby(t, 'Process');

//   await t.click(e.configurationButton);
//   await t.click(Common.toggleElement('Show absolute value'));
//   await t.click(e.configurationButton);

//   await t
//     .resizeWindow(1600, 800)
//     .takeScreenshot('additional-features/img/report-with-process-variants.png', {
//       fullPage: true,
//     });
// });

// test('Display precision properly', async (t) => {
//   await u.createNewReport(t);
//   await u.selectReportDefinition(t, 'Order process');

//   await t.typeText(Common.nameEditField, 'Precision Report', {replace: true});

//   await u.selectView(t, 'Flow node', 'Duration');
//   await t.click(e.addMeasureButton);
//   await t.click(Common.menuOption('Count'));
//   await u.selectVisualization(t, 'Table');

//   await u.selectGroupby(t, 'Start date', 'Automatic');

//   await t.click(e.nextPageButton);

//   let a = e.tableCell(15, 1);
//   let b = e.tableCell(15, 2);
//   let c = e.tableCell(15, 3);

//   // Default precision for duration is 3
//   // shouldn't affect percentage values
//   await t.expect(a.textContent).match(/\d+/);
//   await t.expect(b.textContent).match(/\d(\.\d)?%/);
//   await t.expect(c.textContent).match(/\d+.[a-zA-Z]*.\d+.[a-zA-Z]*.\d+.[a-zA-Z]*/);

//   await t.click(e.configurationButton);
//   await t.click(e.limitPrecisionSwitch);
//   await t.typeText(e.limitPrecisionInput, '4', {replace: true});

//   a = e.tableCell(15, 1);
//   b = e.tableCell(15, 2);
//   c = e.tableCell(15, 3);

//   await t.expect(a.textContent).match(/\d+/);
//   await t.expect(b.textContent).match(/\d(\.\d)?%/);
//   await t.expect(c.textContent).match(/\d+.[a-zA-Z]*.\d+.[a-zA-Z]*.\d+.[a-zA-Z]*.\d+.[a-zA-Z]*/);

//   await t.typeText(e.limitPrecisionInput, '1', {replace: true});

//   a = e.tableCell(15, 1);
//   b = e.tableCell(15, 2);
//   c = e.tableCell(15, 3);

//   await t.expect(a.textContent).match(/\d+/);
//   await t.expect(b.textContent).match(/\d(\.\d)?%/);
//   await t.expect(c.textContent).match(/\d+.[a-zA-Z]*/);
// });

test('add, edit and remove reports description', async (t) => {
  await u.createNewReport(t);

  // Add description
  await t.expect(Common.descriptionParagraph.exists).notOk();
  await t.expect(Common.addDescriptionButton.textContent).contains('Add description');
  const description = 'This is a description of the report.';
  await u.addEditEntityDescription(t, description, 'img/report-descriptionModal.png');

  await t.expect(Common.descriptionField.textContent).contains(description);

  await u.save(t);
  await u.gotoOverview(t);
  await t.expect(Common.listItem('report').textContent).contains(description);

  await t.click(Common.listItemLink('report'));
  await t.expect(Common.descriptionField.textContent).contains(description);

  // Edit description
  await t.resizeWindow(1200, 600);
  await t.click(Common.editButton);

  const newDescription =
    'This is a new description of the report. This time the description is very long and it will not fit in one line. It will display ellipsis and More button.'.repeat(
      2
    );
  await u.addEditEntityDescription(t, newDescription);

  await t.expect(Common.descriptionField.textContent).contains(newDescription);

  await u.save(t);

  // Toggle show less/more
  await t.expect(Common.descriptionField.find('p').hasClass('overflowHidden')).ok();
  await t.expect(Common.showLessMoreDescriptionButton.textContent).contains('More');

  await t.click(Common.showLessMoreDescriptionButton);

  await t.expect(Common.descriptionField.find('p').hasClass('overflowHidden')).notOk();
  await t.expect(Common.showLessMoreDescriptionButton.textContent).contains('Less');

  await t.takeElementScreenshot(
    e.reportContainer,
    'process-analysis/report-analysis/img/report-showMoreDescription.png',
    {
      crop: {bottom: 200},
    }
  );

  await t.click(Common.showLessMoreDescriptionButton);
  await t.expect(Common.descriptionField.find('p').hasClass('overflowHidden')).ok();
  await t.expect(Common.showLessMoreDescriptionButton.textContent).contains('More');

  // Remove description
  await t.click(Common.editButton);
  await u.addEditEntityDescription(t);
  await t.expect(Common.descriptionParagraph.exists).notOk();
  await t.expect(Common.addDescriptionButton.textContent).contains('Add description');

  await u.save(t);

  await t.expect(Common.descriptionField.exists).notOk();
});

test('change popover alignment and height to stay visible', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');

  await t.click(e.definitionEditor);

  // alignment
  await t.expect(e.definitionEditorPopover.hasClass('cds--popover--bottom-end')).ok();

  // height adjustment
  await t.resizeWindow(1200, 600);
  const dialogHeight = await e.definitionEditorDialog.getStyleProperty('height');
  await t.expect(Number(dialogHeight.replace('px', ''))).lt(300);

  // vertical flip
  await t.resizeWindow(1200, 300);
  await t.expect(e.definitionEditorPopover.hasClass('cds--popover--top-end')).ok();
});

test('display raw data report in collapsible section under the main report renderer', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');

  await u.selectView(t, 'Flow node', 'Duration');

  await t.expect(e.reportDiagram.exists).ok();
  await t.expect(e.collapsibleContainerTable.exists).notOk();

  await t.click(e.collapsibleContainerExpandButton);

  await t.expect(e.reportDiagram.exists).ok();
  await t.expect(e.collapsibleContainerTable.exists).ok();

  await t.click(e.collapsibleContainerExpandButton);

  await t.expect(e.reportDiagram.exists).notOk();
  await t.expect(e.collapsibleContainerTable.exists).ok();

  await t.click(e.collapsibleContainerCollapseButton);

  await t.expect(e.reportDiagram.exists).ok();
  await t.expect(e.collapsibleContainerTable.exists).ok();

  await t.click(e.collapsibleContainerCollapseButton);

  await t.expect(e.reportDiagram.exists).ok();
  await t.expect(e.collapsibleContainerTable.exists).notOk();
});

test('hide collapsible section if report has table view', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Order process');

  await u.selectView(t, 'Flow node', 'Duration');

  await t.expect(e.collapsibleContainer.exists).ok();

  await u.selectVisualization(t, 'Table');

  await t.expect(e.collapsibleContainer.exists).notOk();
});
