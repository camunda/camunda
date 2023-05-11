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
import * as Filter from './Filter.elements.js';
import * as Common from './Common.elements';

fixture('Process Report Filter').page(config.endpoint).beforeEach(u.login).afterEach(cleanEntities);

test('variable filter modal dependent on variable type', async (t) => {
  await u.createNewReport(t);

  await u.selectReportDefinition(t, 'Lead Qualification');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Variable'));

  await t.click(Common.typeahead);
  await t.typeText(Filter.variableTypeahead, 'dc', {replace: true});

  await t.takeElementScreenshot(Common.modalContainer, 'process-analysis/img/variable-filter.png');

  await t.typeText(Filter.variableTypeahead, 'boolVar', {replace: true});
  await t.click(Common.typeaheadOption('boolVar'));
  await t.click(Filter.firstMultiSelectValue);

  await t.typeText(Filter.variableTypeahead, 'stringVar', {replace: true});
  await t.click(Common.typeaheadOption('stringVar'));

  await t.expect(Filter.stringValues.textContent).contains('aStringValue');

  await t.click(Filter.variableFilterOperatorButton('contains'));
  await t
    .typeText(Filter.variableFilterValueInput, 'aSubString', {replace: true})
    .pressKey('tab')
    .typeText(Filter.variableFilterValueInput, 'anotherSubstring')
    .pressKey('tab');

  await t.typeText(Filter.variableTypeahead, 'integerVar', {replace: true});
  await t.click(Common.typeaheadOption('integerVar'));

  await t
    .typeText(Filter.variableFilterValueInput, '14', {replace: true})
    .pressKey('tab 3 0 tab 1 0 0 tab');

  await t.click(Filter.variableOrButton);

  await t.typeText(Filter.variableTypeahead, 'dateVar', {replace: true});
  await t.click(Common.typeaheadOption('dateVar'));
  await t.click(Filter.dateFilterTypeSelect);
  await t.click(Filter.dateFilterTypeOption('Between'));
  await t.click(Filter.dateFilterStartInput);
  await t.click(Filter.pickerDate('5'));
  await t.click(Filter.pickerDate('22')).wait(200);
  await t.click(Filter.dateFilterEndInput);

  await t.click(Filter.variableHeader('integerVar'));

  await t.click(Filter.removeVariableBtn);
});

test('filter for custom string variable values', async (t) => {
  await u.createNewReport(t);

  await u.selectReportDefinition(t, 'Lead Qualification');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Variable'));

  await t.typeText(Filter.variableTypeahead, 'stringVar', {replace: true});
  await t.click(Common.typeaheadOption('stringVar'));

  await t.expect(Filter.stringValues.textContent).contains('aStringValue');

  await t.click(Filter.addValueButton);
  await t.typeText(Filter.customValueInput, 'custom value', {replace: true});
  await t.click(Filter.addValueToListButton);

  await t.expect(Filter.stringValues.textContent).contains('custom value');

  await t.click(Common.carbonModalConfirmBtn);

  await t.expect(Report.controlPanelFilter.textContent).contains('custom value');
});

test('should apply a filter to the report result', async (t) => {
  await u.createNewReport(t);

  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(Report.sectionToggle('Filters'));

  const unfiltered = +(await Report.reportNumber.textContent);

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Variable'));

  await t.click(Common.typeahead);
  await t.click(Common.typeaheadOption('amount'));
  await t.click(Filter.variableFilterOperatorButton('is less than'));

  await t.typeText(Filter.variableFilterValueInput, '100', {replace: true});

  await t.click(Common.carbonModalConfirmBtn);

  const filtered = +(await Report.reportNumber.textContent);

  await t.expect(unfiltered).gt(filtered);
});

test('instance state filters', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance State'));
  await t.click(Report.modalOption('Running'));
  await t.click(Common.carbonModalConfirmBtn);
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance State'));
  await t.click(Report.modalOption('Completed'));
  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Report.warningMessage.visible).ok();
  await t.click(Report.filterRemoveButton);
  await t.click(Report.filterRemoveButton);
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance State'));
  await t.click(Report.modalOption('Canceled'));
  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Report.reportRenderer.visible).ok();
  await t.click(Report.filterRemoveButton);
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance State'));
  await t.click(Report.modalOption('Non Canceled'));
  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Report.reportRenderer.visible).ok();
  await t.click(Report.filterRemoveButton);
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance State'));
  await t.click(Report.modalOption('Suspended'));
  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Report.reportRenderer.visible).ok();
  await t.click(Report.filterRemoveButton);
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance State'));
  await t.click(Report.modalOption('Non Suspended'));
  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('pick a start date from the date picker', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Lead Qualification');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance Date'));
  await t.click(Report.subFilterOption('Start Date'));
  await t.click(Filter.dateTypeSelect);
  await t.click(Common.option('Between'));
  await t.click(Filter.dateFilterStartInput);
  await t.click(Filter.pickerDate('5'));
  await t.click(Filter.pickerDate('22'));
  await t.click(Filter.infoText);

  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Report.reportRenderer.visible).ok();

  await t.click(Filter.editButton);
  await t.click(Filter.dateTypeSelect);
  await t.click(Common.option('After'));
  await t.click(Filter.dateFilterStartInput);
  await t.click(Filter.pickerDate('5'));
  await t.click(Filter.infoText);
  await t.click(Common.carbonModalConfirmBtn);

  await t.expect(Report.reportRenderer.visible).ok();
});

test('add relative current month start date filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance Date'));
  await t.click(Report.subFilterOption('Start Date'));
  await t.click(Filter.dateTypeSelect);
  await t.click(Common.option('This...'));
  await t.click(Filter.unitSelect);
  await t.click(Common.option('month'));

  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add rolling last 5 days end date filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance Date'));
  await t.click(Report.subFilterOption('End Date'));
  await t.click(Filter.dateTypeSelect);
  await t.click(Common.option('Rolling'));
  await t.click(Filter.unitSelect);
  await t.click(Common.option('days'));
  await t.typeText(Filter.customDateInput, '5', {replace: true});

  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add process instance duration filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance Duration'));
  await t.click(Report.subFilterOption('Process instance'));
  await t.click(Filter.durationFilterOperator);
  await t.click(Common.option('less than'));

  await t.typeText(Filter.durationFilterInput, '30', {replace: true});

  await t.takeElementScreenshot(Common.modalContainer, 'process-analysis/img/duration-filter.png');

  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add flow node duration filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance Duration'));
  await t.click(Report.subFilterOption('Flow Node'));

  await t.typeText(Report.targetValueInput('Approve Invoice'), '1');
  await t.typeText(Report.targetValueInput('Prepare Bank Transfer'), '5');
  await t.click(Report.nodeFilterOperator('Prepare Bank Transfer'));
  await t.click(Report.dropdownOption('less than'));
  await t.typeText(Report.targetValueInput('Review Invoice'), '15');

  await t.resizeWindow(1650, 850);
  await t.takeElementScreenshot(
    Common.modalContainer,
    'process-analysis/img/flowNode-duration-filter.png'
  );

  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add assignee filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.click(Report.sectionToggle('Filters'));

  await t.click(Report.flowNodeFilterButton);
  await t.click(Report.filterOption('Assignee'));

  await t.click(Filter.multiSelect);
  await t.typeText(Filter.multiSelect, 'er', {replace: true});
  await t.click(Filter.multiSelectOptionNumber(0));

  await t.takeElementScreenshot(Common.modalContainer, 'process-analysis/img/assignee-filter.png');

  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('add Flow Node filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');
  await t.click(Report.sectionToggle('Filters'));

  await t.resizeWindow(1000, 750);

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Flow Node Execution'));

  await t.click(Report.flowNode('approveInvoice'));
  await t.click(Report.flowNode('reviewInvoice'));

  await t
    .takeElementScreenshot(Common.modalContainer, 'process-analysis/img/flownode-filter.png')
    .maximizeWindow();

  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Report.reportRenderer.visible).ok();
});

test('the filter is visible in the control panel and contains correct information', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Flow Node Execution'));
  await t.click(Report.flowNode('approveInvoice'));
  await t.click(Common.carbonModalConfirmBtn);
  const controlPanelFilterText = Report.controlPanelFilter.textContent;

  await t.expect(controlPanelFilterText).contains('Running, Canceled or Completed');
  await t.expect(controlPanelFilterText).contains('Approve Invoice');

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance Date'));
  await t.click(Report.subFilterOption('Start Date'));

  await t.click(Filter.dateTypeSelect);
  await t.click(Common.option('This...'));
  await t.click(Filter.unitSelect);
  await t.click(Common.option('month'));
  await t.click(Common.carbonModalConfirmBtn);

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance State'));
  await t.click(Report.modalOption('Running'));
  await t.click(Common.carbonModalConfirmBtn);

  await t.resizeWindow(1300, 900);

  await u.selectView(t, 'Flow Node', 'Count');
  await u.selectGroupby(t, 'Flow Nodes');
  await u.selectVisualization(t, 'Heatmap');

  await t
    .takeScreenshot('process-analysis/img/combined-filter.png', {fullPage: true})
    .maximizeWindow();
});

test('add an incident filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Incident Process');
  await u.selectView(t, 'Incident', 'Resolution Duration');

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);

  await t.click(Report.filterOption('Incident'));
  await t.click(Report.modalOption('Open Incidents'));
  await t.click(Common.carbonModalConfirmBtn);

  await t.expect(Report.reportRenderer.visible).ok();
  await t.click(Report.filterRemoveButton);

  await t.click(Report.flowNodeFilterButton);

  await t.click(Report.filterOption('Incident'));
  await t.click(Report.modalOption('Resolved Incidents'));
  await t.click(Common.carbonModalConfirmBtn);

  await t.expect(Report.reportRenderer.visible).ok();
});

test('add flow node status filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Flow Node', 'Count');

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.flowNodeFilterButton);

  await t.click(Report.filterOption('Flow Node State'));
  await t.click(Report.modalOption('Running'));
  await t.click(Common.carbonModalConfirmBtn);

  await t.expect(Report.reportRenderer.visible).ok();
  await t.click(Report.filterRemoveButton);
  await t.click(Report.flowNodeFilterButton);

  await t.click(Report.filterOption('Flow Node State'));
  await t.click(Report.modalOption('Completed or Canceled'));
  await t.click(Common.carbonModalConfirmBtn);

  await t.expect(Report.reportRenderer.visible).ok();
});

test('select which flow nodes to show from the configuration', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Flow Node', 'Count');
  await u.selectVisualization(t, 'Table');

  await t.expect(Report.nodeTableCell('Assign Approver Group').exists).ok();

  await t.resizeWindow(1150, 800);

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.flowNodeFilterButton);
  await t.click(Report.filterOption('Flow Node Selection'));

  await t.click(Report.deselectAllButton);

  await t.click(Report.flowNode('approveInvoice'));
  await t.click(Report.flowNode('reviewInvoice'));
  await t.click(Report.flowNode('prepareBankTransfer'));

  await t
    .takeElementScreenshot(Common.modalContainer, 'process-analysis/img/flowNodeSelection.png')
    .maximizeWindow();

  await t.click(Common.carbonModalConfirmBtn);

  await t.expect(Report.nodeTableCell('Assign Approver Group').exists).notOk();
});

test('multi definition filters', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectReportDefinition(t, 'Hiring Demo 5 Tenants');
  await u.selectReportDefinition(t, 'Book Request One Tenant');
  await u.selectReportDefinition(t, 'Embedded Subprocess');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.expect(Report.reportRenderer.visible).ok();

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Instance State'));

  await t.click(Report.modalOption('Running'));
  await t.click(Filter.removeButtonFor('All included processes'));
  await t.click(Filter.stateFilterMultiSelect);
  await t.click(Filter.stateFilterMultiSelectOption('Hiring Demo 5 Tenants'));
  await t.click(Filter.stateFilterMultiSelect);
  await t.click(Filter.stateFilterMultiSelectOption('Book Request One Tenant'));

  await t.click(Common.carbonModalConfirmBtn);

  await t.expect(Report.reportRenderer.visible).ok();
  await t.expect(Common.controlPanel.textContent).contains('Applied to: 2 Processes');

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Flow Node Execution'));

  await t.click(Common.typeahead);
  await t.click(Common.typeaheadOption('Embedded Subprocess'));

  await t.click(Report.flowNode('Task_0th4ivq'));
  await t.click(Report.flowNode('Task_1q83i19'));

  await t.click(Common.carbonModalConfirmBtn);

  await t.expect(Report.reportRenderer.visible).ok();

  await t.expect(Common.controlPanel.textContent).contains('Assess Credit Worthiness');
  await t.expect(Common.controlPanel.textContent).contains('Register Application');
});

test('add flow node start date filter', async (t) => {
  await u.createNewReport(t);
  await u.selectReportDefinition(t, 'Invoice Receipt with alternative correlation variable');
  await u.selectView(t, 'Process Instance', 'Count');

  await t.resizeWindow(1150, 800);

  await t.click(Report.sectionToggle('Filters'));

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Flow Node Date'));
  await t.click(Report.subFilterOption('Start Date'));

  await t.click(Filter.dateTypeSelect);
  await t.click(Common.option('This...'));
  await t.click(Filter.unitSelect);
  await t.click(Common.option('year'));

  await t.click(Report.flowNode('approveInvoice'));
  await t.click(Report.flowNode('reviewInvoice'));

  await t
    .takeElementScreenshot(Common.modalContainer, 'process-analysis/img/flowNode-date-filter.png')
    .maximizeWindow();

  await t.click(Common.carbonModalConfirmBtn);
  await t.expect(Report.reportRenderer.visible).ok();
});
