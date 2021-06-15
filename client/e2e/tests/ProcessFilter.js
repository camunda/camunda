/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Report from './ProcessReport.elements.js';
import * as Filter from './Filter.elements.js';

fixture('Process Report Filter').page(config.endpoint).beforeEach(u.login).afterEach(cleanEntities);

test('variable filter modal dependent on variable type', async (t) => {
  await u.createNewReport(t);

  await u.selectReportDefinition(t, 'Lead Qualification', 'All');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Variable'));

  await t.click(Filter.typeahead);
  await t.typeText(Filter.typeaheadInput, 'dc', {replace: true});

  await t.takeElementScreenshot(Report.modalContainer, 'process/filter/variable-filter.png');

  await t.typeText(Filter.typeaheadInput, 'boolVar', {replace: true});
  await t.click(Filter.typeaheadOption('boolVar'));
  await t.click(Filter.firstMultiSelectValue);

  await t.takeElementScreenshot(
    Report.modalContainer,
    'process/filter/variable-filter-boolean.png'
  );

  await t.typeText(Filter.typeaheadInput, 'stringVar', {replace: true});
  await t.click(Filter.typeaheadOption('stringVar'));

  await t.expect(Filter.stringValues.textContent).contains('aStringValue');

  await t.takeElementScreenshot(Report.modalContainer, 'process/filter/variable-filter-string.png');

  await t.click(Filter.variableFilterOperatorButton('contains'));
  await t.typeText(Filter.variableFilterValueInput, 'aSubString anotherSubstring', {replace: true});

  await t.typeText(Filter.typeaheadInput, 'integerVar', {replace: true});
  await t.click(Filter.typeaheadOption('integerVar'));

  await t.typeText(Filter.variableFilterValueInput, '14 30 100', {replace: true});

  await t.takeElementScreenshot(
    Report.modalContainer,
    'process/filter/variable-filter-numeric.png'
  );

  await t.typeText(Filter.typeaheadInput, 'dateVar', {replace: true});
  await t.click(Filter.typeaheadOption('dateVar'));
  await t.click(Filter.dateFilterTypeSelect);
  await t.click(Filter.dateFilterTypeOption('Fixed Date'));
  await t.click(Filter.dateFilterStartInput);
  await t.click(Filter.pickerDate('5'));
  await t.click(Filter.pickerDate('22')).wait(200);
  await t.click(Filter.dateFilterEndInput);

  await t.takeElementScreenshot(Report.modalContainer, 'process/filter/variable-filter-date.png');
});

test('filter for custom string variable values', async (t) => {
  await u.createNewReport(t);

  await u.selectReportDefinition(t, 'Lead Qualification', 'All');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Variable'));

  await t.typeText(Filter.typeaheadInput, 'stringVar', {replace: true});
  await t.click(Filter.typeaheadOption('stringVar'));

  await t.expect(Filter.stringValues.textContent).contains('aStringValue');

  await t.click(Filter.addValueButton);
  await t.typeText(Filter.customValueInput, 'custom value', {replace: true});
  await t.click(Filter.addValueToListButton);

  await t.expect(Filter.stringValues.textContent).contains('custom value');

  await t.click(Report.primaryModalButton);

  await t.expect(Report.controlPanelFilter.textContent).contains('custom value');
});

test('should apply a filter to the report result', async (t) => {
  await u.createNewReport(t);

  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(Report.sectionToggle('Filters'));

  const unfiltered = +(await Report.reportNumber.textContent);

  await t
    .resizeWindow(1400, 850)
    .click(Report.filterButton)
    .takeElementScreenshot(Report.controlPanel, 'process/filter/report-with-filterlist-open.png', {
      crop: {top: 220},
    })
    .maximizeWindow();

  await t.click(Report.filterOption('Variable'));

  await t.click(Filter.typeahead);
  await t.click(Filter.typeaheadOption('amount'));
  await t.click(Filter.variableFilterOperatorButton('is less than'));

  await t.typeText(Filter.variableFilterValueInput, '100', {replace: true});

  await t.click(Report.primaryModalButton);

  const filtered = +(await Report.reportNumber.textContent);

  await t.expect(unfiltered).gt(filtered);
});

test('instance state filters', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Process Instance State'));
  await t.click(Report.modalOption('Running'));
  await t.click(Report.primaryModalButton);
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Process Instance State'));
  await t.click(Report.modalOption('Completed'));
  await t.click(Report.primaryModalButton);
  await t.expect(Report.warningMessage.visible).ok();
  await t.click(Report.filterRemoveButton);
  await t.click(Report.filterRemoveButton);
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Process Instance State'));
  await t.click(Report.modalOption('Canceled'));
  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
  await t.click(Report.filterRemoveButton);
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Process Instance State'));
  await t.click(Report.modalOption('Non Canceled'));
  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
  await t.click(Report.filterRemoveButton);
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Process Instance State'));
  await t.click(Report.modalOption('Suspended'));
  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
  await t.click(Report.filterRemoveButton);
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Process Instance State'));
  await t.click(Report.modalOption('Non Suspended'));
  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('pick a start date from the date picker', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Lead Qualification');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Date'));
  await t.click(Report.subFilterOption('Start Date'));
  await t.click(Filter.dateTypeSelect);
  await t.click(Report.option('Fixed Date'));
  await t.click(Filter.dateFilterStartInput);
  await t.click(Filter.pickerDate('5'));
  await t.click(Filter.pickerDate('22'));
  await t.click(Filter.infoText);

  await t.takeElementScreenshot(
    Report.modalContainer,
    'process/filter/fixed-start-date-filter.png'
  );

  await t.click(Report.primaryModalButton);

  await t.expect(Report.reportRenderer.visible).ok();
});

test('add relative current month start date filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Date'));
  await t.click(Report.subFilterOption('Start Date'));
  await t.click(Filter.dateTypeSelect);
  await t.click(Report.option('This...'));
  await t.click(Filter.unitSelect);
  await t.click(Report.option('month'));

  await t.takeElementScreenshot(
    Report.modalContainer,
    'process/filter/relative-start-date-filter.png'
  );

  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add rolling last 5 days end date filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Date'));
  await t.click(Report.subFilterOption('End Date'));
  await t.click(Filter.dateTypeSelect);
  await t.click(Report.option('Rolling'));
  await t.click(Filter.unitSelect);
  await t.click(Report.option('days'));
  await t.typeText(Filter.customDateInput, '5', {replace: true});

  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add process instance duration filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Duration'));
  await t.click(Report.subFilterOption('Process instance'));
  await t.click(Filter.durationFilterOperator);
  await t.click(Report.option('less than'));

  await t.typeText(Filter.durationFilterInput, '30', {replace: true});

  await t.takeElementScreenshot(Report.modalContainer, 'process/filter/duration-filter.png');

  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add flow node duration filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Duration'));
  await t.click(Report.subFilterOption('Flow Node'));

  await t.typeText(Report.targetValueInput('Approve Invoice'), '1');
  await t.typeText(Report.targetValueInput('Prepare Bank Transfer'), '5');
  await t.click(Report.nodeFilterOperator('Prepare Bank Transfer'));
  await t.click(Report.dropdownOption('less than'));
  await t.typeText(Report.targetValueInput('Review Invoice'), '15');

  await t.resizeWindow(1650, 850);
  await t.takeElementScreenshot(
    Report.modalContainer,
    'process/filter/flowNode-duration-filter.png'
  );

  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add assignee filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(Report.sectionToggle('Filters'));

  await t
    .resizeWindow(1400, 850)
    .click(Report.flowNodeFilterButton)
    .takeElementScreenshot(
      Report.controlPanel,
      'process/filter/report-with-flownode-filterlist-open.png',
      {
        crop: {top: 220},
      }
    );

  await t.click(Report.filterOption('Assignee'));

  await t.click(Filter.multiSelect);
  await t.typeText(Filter.multiSelect, 'er', {replace: true});
  await t.click(Filter.multiSelectOptionNumber(0));

  await t.takeElementScreenshot(Report.modalContainer, 'process/filter/assignee-filter.png');

  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add Flow Node filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await t.click(Report.sectionToggle('Filters'));

  await t.resizeWindow(1000, 700);

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Flow Node'));

  await t.click(Report.flowNode('approveInvoice'));
  await t.click(Report.flowNode('reviewInvoice'));

  await t
    .takeElementScreenshot(Report.modalContainer, 'process/filter/flownode-filter.png')
    .maximizeWindow();

  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('the filter is visible in the control panel and contains correct information', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Flow Node'));
  await t.click(Report.flowNode('approveInvoice'));
  await t.click(Report.primaryModalButton);
  const controlPanelFilterText = Report.controlPanelFilter.textContent;

  await t.expect(controlPanelFilterText).contains('Running, Canceled or Completed');
  await t.expect(controlPanelFilterText).contains('Approve Invoice');

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Date'));
  await t.click(Report.subFilterOption('Start Date'));

  await t.click(Filter.dateTypeSelect);
  await t.click(Report.option('This...'));
  await t.click(Filter.unitSelect);
  await t.click(Report.option('month'));
  await t.click(Report.primaryModalButton);

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Process Instance State'));
  await t.click(Report.modalOption('Running'));
  await t.click(Report.primaryModalButton);

  await t.resizeWindow(1300, 900);

  await u.selectView(t, 'Flow Node', 'Count');
  await u.selectGroupby(t, 'Flow Nodes');
  await u.selectVisualization(t, 'Heatmap');

  await t.takeScreenshot('process/filter/combined-filter.png', {fullPage: true}).maximizeWindow();
});

test('add an incident filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Incident Process', 'All');
  await u.selectView(t, 'Incident', 'Resolution Duration');

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);

  await t.click(Report.filterOption('Incident'));
  await t.click(Report.modalOption('Open Incidents'));
  await t.click(Report.primaryModalButton);

  await t.expect(Report.reportRenderer.visible).ok();
  await t.click(Report.filterRemoveButton);

  await t.click(Report.flowNodeFilterButton);

  await t.click(Report.filterOption('Incident'));
  await t.click(Report.modalOption('Resolved Incidents'));
  await t.click(Report.primaryModalButton);

  await t.expect(Report.reportRenderer.visible).ok();
});

test('add flow node status filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Flow Node', 'Count');

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.flowNodeFilterButton);

  await t.click(Report.filterOption('Flow Node State'));
  await t.click(Report.modalOption('Running'));
  await t.click(Report.primaryModalButton);

  await t.expect(Report.reportRenderer.visible).ok();
  await t.click(Report.filterRemoveButton);
  await t.click(Report.flowNodeFilterButton);

  await t.click(Report.filterOption('Flow Node State'));
  await t.click(Report.modalOption('Completed or Canceled'));
  await t.click(Report.primaryModalButton);

  await t.expect(Report.reportRenderer.visible).ok();
});
