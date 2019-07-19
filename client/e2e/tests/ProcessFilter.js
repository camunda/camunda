/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Report from './ProcessReport.elements.js';
import * as Filter from './Filter.elements.js';

fixture('Process Report Filter')
  .page(config.endpoint)
  .before(setup)
  .beforeEach(u.login);

test('should apply a filter to the report result', async t => {
  await u.createNewReport(t);

  await u.selectDefinition(t, 'Invoice Receipt');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');

  const unfiltered = +(await Report.reportRenderer.textContent);

  await t.click(Report.filterButton);
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
  await t.click(Filter.dateFilterStartInput);
  await t.click(Filter.pickerDate('5'));
  await t.click(Filter.pickerDate('8'));

  await t.click(Report.primaryModalButton);

  await t.expect(Report.reportRenderer.visible).ok();
});

test('pick a start date from the predefined buttons', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Start Date'));

  await t.click(Filter.yearFilterButton);
  const filterStart = await Filter.dateFilterStartInput.value;
  const filterEnd = await Filter.dateFilterEndInput.value;
  await t.expect(filterStart).eql(`${new Date().getFullYear()}-01-01`);
  await t.expect(filterEnd).eql(`${new Date().getFullYear()}-12-31`);

  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add relative end date filter', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('End Date'));
  await t.click(Filter.relativeDateButton);
  await t.typeText(Filter.relativeDateInput, '5', {replace: true});
  await t.click(Filter.relativeDateDropdown);
  await t.click(Report.option('Months'));
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
  await t.click(Report.option('less'));

  await t.typeText(Filter.durationFilterInput, '30', {replace: true});

  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add Flow Node filter', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'None');
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Flow Node'));

  await t.click(Report.flowNode('reviewInvoice'));
  await t.click(Report.flowNode('ServiceTask_1'));

  await t.click(Report.primaryModalButton);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('the filter is visible in the control panel and contains correct information', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt');
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Flow Node'));
  await t.click(Report.flowNode('reviewInvoice'));
  await t.click(Report.primaryModalButton);
  const controlPanelFilterText = Report.controlPanelFilter.textContent;

  await t.expect(controlPanelFilterText).contains('Executed Flow Node');
  await t.expect(controlPanelFilterText).contains('Review Invoice');
});
