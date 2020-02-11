/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {beforeAllTests, cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Report from './ProcessReport.elements.js';
import * as Filter from './Filter.elements.js';

fixture('Process Report Filter')
  .page(config.endpoint)
  .before(beforeAllTests)
  .beforeEach(u.login)
  .afterEach(cleanEntities);

test('variable filter modal dependent on variable type', async t => {
  await u.createNewReport(t);

  await u.selectDefinition(t, 'Lead Qualification', 'All');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Variable'));

  await t.click(Filter.variableFilterTypeahead);
  await t.typeText(Filter.variableFilterTypeaheadInput, 'dc', {replace: true});

  await t.takeElementScreenshot(Report.modalContainer, 'process/filter/variable-filter.png');

  await t.typeText(Filter.variableFilterTypeaheadInput, 'boolVar', {replace: true});
  await t.click(Filter.variableFilterTypeaheadOption('boolVar'));

  await t.takeElementScreenshot(
    Report.modalContainer,
    'process/filter/variable-filter-boolean.png'
  );

  await t.typeText(Filter.variableFilterTypeaheadInput, 'stringVar', {replace: true});
  await t.click(Filter.variableFilterTypeaheadOption('stringVar'));

  await t.expect(Filter.stringValues.textContent).contains('aStringValue');

  await t.takeElementScreenshot(Report.modalContainer, 'process/filter/variable-filter-string.png');

  await t.typeText(Filter.variableFilterTypeaheadInput, 'integerVar', {replace: true});
  await t.click(Filter.variableFilterTypeaheadOption('integerVar'));

  await t.typeText(Filter.variableFilterValueInput, '14', {replace: true});
  await t.click(Filter.addValueButton);
  await t.typeText(Filter.variableFilterValueInput, '30', {replace: true});
  await t.click(Filter.addValueButton);
  await t.typeText(Filter.variableFilterValueInput, '100', {replace: true});

  await t.takeElementScreenshot(
    Report.modalContainer,
    'process/filter/variable-filter-numeric.png'
  );

  await t.click(Filter.nullSwitch);

  await t.takeElementScreenshot(
    Report.modalContainer,
    'process/filter/variable-filter-undefinedOrNull.png'
  );

  await t.typeText(Filter.variableFilterTypeaheadInput, 'dateVar', {replace: true});
  await t.click(Filter.variableFilterTypeaheadOption('dateVar'));
  await t.click(Filter.dateFilterStartInput);
  await t.click(Filter.pickerDate('1'));
  await t.click(Filter.pickerDate('7'));
  await t.click(Filter.dateFilterEndInput);

  await t.takeElementScreenshot(Report.modalContainer, 'process/filter/variable-filter-date.png');
});

test('should apply a filter to the report result', async t => {
  await u.createNewReport(t);

  await u.selectDefinition(t, 'Invoice Receipt');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');

  const unfiltered = +(await Report.reportRenderer.textContent);

  await t.click(Report.filterButton);

  await t
    .resizeWindow(1400, 700)
    .takeElementScreenshot(Report.controlPanel, 'process/filter/report-with-filterlist-open.png')
    .maximizeWindow();

  await t.click(Report.filterOption('Variable'));

  await t.click(Filter.variableFilterTypeahead);
  await t.click(Filter.variableFilterTypeaheadOption('amount'));
  await t.click(Filter.variableFilterOperatorButton('is less than'));

  await t.typeText(Filter.variableFilterValueInput, '100', {replace: true});

  await t.click(Report.primaryModalButton);

  const filtered = +(await Report.reportRenderer.textContent);

  await t.expect(unfiltered).gt(filtered);
});

test('instance state filters', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Running Instances Only'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Completed Instances Only'));
  await t.expect(Report.warningMessage.visible).ok();
  await t.click(Report.filterRemoveButton);
  await t.click(Report.filterRemoveButton);
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Canceled Instances Only'));
  await t.expect(Report.reportRenderer.visible).ok();
  await t.click(Report.filterRemoveButton);
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Non Canceled Instances Only'));
  await t.expect(Report.reportRenderer.visible).ok();
});

test('pick a start date from the date picker', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Lead Qualification');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Start Date'));
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

test('add relative current month start date filter', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Start Date'));
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

test('add rolling last 5 days end date filter', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('End Date'));
  await t.click(Filter.dateTypeSelect);
  await t.click(Report.option('Rolling'));
  await t.click(Filter.unitSelect);
  await t.click(Report.option('days'));
  await t.typeText(Filter.customDateInput, '5', {replace: true});

  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add duration filter', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Duration'));
  await t.click(Filter.durationFilterOperator);
  await t.click(Report.option('less than'));

  await t.typeText(Filter.durationFilterInput, '30', {replace: true});

  await t.takeElementScreenshot(Report.modalContainer, 'process/filter/duration-filter.png');

  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add Flow Node filter', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');

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

test('the filter is visible in the control panel and contains correct information', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt', 'All');

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Flow Node'));
  await t.click(Report.flowNode('approveInvoice'));
  await t.click(Report.primaryModalButton);
  const controlPanelFilterText = Report.controlPanelFilter.textContent;

  await t.expect(controlPanelFilterText).contains('Executed Flow Node');
  await t.expect(controlPanelFilterText).contains('Approve Invoice');

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Start Date'));

  await t.click(Filter.dateTypeSelect);
  await t.click(Report.option('Last...'));
  await t.click(Filter.unitSelect);
  await t.click(Report.option('month'));
  await t.click(Report.primaryModalButton);

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Running Instances Only'));

  await t.resizeWindow(1300, 900);

  await u.selectView(t, 'Flow Node', 'Count');
  await u.selectVisualization(t, 'Heatmap');

  await t.takeScreenshot('process/filter/combined-filter.png', {fullPage: true}).maximizeWindow();
});
