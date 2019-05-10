/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as e from './SingleReport.elements.js';

fixture('Report')
  .page(config.endpoint)
  .before(setup)
  .beforeEach(u.login);

test('create and name a report', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt', '2');
  await u.selectView(t, 'Raw Data');

  await t.typeText(e.nameEditField, 'New Name', {replace: true});

  await u.save(t);

  await t.expect(e.reportName.textContent).eql('New Name');
  await t.expect(e.reportRenderer.textContent).contains('invoice');
  await t.expect(e.reportRenderer.textContent).contains('Start Date');
});

test('sort table columns', async t => {
  await t.hover(e.report);
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
  await t.click(e.editButton);

  await t.click(e.configurationButton);

  await t.click(e.columnSwitch('Start Date'));

  await t.expect(e.reportRenderer.textContent).notContains('Start Date');
});

test('cancel changes', async t => {
  await t.hover(e.report);
  await t.click(e.editButton);
  await t.typeText(e.nameEditField, 'Another new Name', {replace: true});

  await u.cancel(t);

  await t.expect(e.reportName.textContent).eql('New Name');
});

test('sharing', async t => {
  await t.click(e.report);
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
  await t.click(e.editButton);

  await u.selectDefinition(t, 'Lead Qualification', '1');

  await t.expect(e.reportRenderer.textContent).notContains('invoice');
  await t.expect(e.reportRenderer.textContent).contains('leadQualification');
});

test('should only enable valid combinations', async t => {
  await t.hover(e.report);
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

  await u.selectGroupby(t, 'Start Date of Process Instance', 'Month');

  await t.click(e.visualizationDropdown);

  await t.expect(e.option('Number').hasAttribute('disabled')).ok();
  await t.expect(e.option('Table').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Bar Chart').hasAttribute('disabled')).notOk();
  await t.expect(e.option('Heatmap').hasAttribute('disabled')).ok();

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

test('different visualizations', async t => {
  await t.hover(e.report);
  await t.click(e.editButton);

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
  await t.click(e.editButton);

  await u.selectDefinition(t, 'Lead Qualification', '1');

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
  await t.click(e.editButton);

  await t.click(e.configurationButton);
  await t.click(e.goalSwitch);
  await t.click(e.configurationButton);

  await t.expect(e.reportProgressBar.visible).ok();
});

test('heatmap target values', async t => {
  await t.hover(e.report);
  await t.click(e.editButton);

  await u.selectView(t, 'Flow Node', 'Duration');
  await u.selectVisualization(t, 'Heatmap');

  await t.hover(e.flowNode('approveInvoice'));

  await t.expect(e.tooltip.textContent).notContains('target\u00A0duration');

  await t.click(e.targetValueButton);
  await t.typeText(e.targetValueInput('Approve Invoice'), '1');
  await t.click(e.targetModalConfirmButton);

  await t.hover(e.flowNode('approveInvoice'));

  await t.expect(e.tooltip.textContent).contains('target\u00A0duration:\u00A01h');

  await t.click(e.targetValueButton);

  await t.hover(e.flowNode('approveInvoice'));

  await t.expect(e.tooltip.textContent).notContains('target\u00A0duration');

  await u.save(t);
});

test('always show tooltips', async t => {
  await t.hover(e.report);
  await t.click(e.editButton);

  await t.expect(e.tooltip.exists).notOk();

  await t.click(e.configurationButton);
  await t.click(e.tooltipSwitch);

  await t.expect(e.tooltip.visible).ok();
});

test('show process instance count', async t => {
  await t.hover(e.report);
  await t.click(e.editButton);

  await t.click(e.configurationButton);
  await t.click(e.instanceCountSwitch);

  await t.expect(e.instanceCount.visible).ok();
  await t.expect(e.instanceCount.textContent).contains('Total InstanceCount:3');
});

test('warning about missing datapoints', async t => {
  await t.hover(e.report);
  await t.click(e.editButton);

  await u.selectDefinition(t, 'Lead Qualification', '1');
  await u.selectView(t, 'Process Instance', 'Count');
  await u.selectGroupby(t, 'Start Date of Process Instance', 'Hour');
  await u.selectVisualization(t, 'Table');

  await t.expect(e.warning.visible).ok();
  await t
    .expect(e.warning.textContent)
    .contains('To refine the set of results, edit your set-up above.');

  await u.save(t);
});

test('process parts', async t => {
  await t.hover(e.report);
  await t.click(e.editButton);

  await u.selectView(t, 'Process Instance', 'Duration');
  await u.selectGroupby(t, 'None');

  const withoutPart = await e.reportNumber.textContent;

  await t.click(e.processPartButton);
  await t.click(e.modalFlowNode('DoBasicLeadQual'));
  await t.click(e.modalFlowNode('ConductDiscoveryCall'));

  await t.click(e.targetModalConfirmButton);

  const withPart = await e.reportNumber.textContent;

  await t.expect(withoutPart).notEql(withPart);
});

test('deleting', async t => {
  await t.click(e.report);

  await t.click(e.deleteButton);
  await t.click(e.modalConfirmbutton);

  await t.expect(e.report.exists).notOk();
});
