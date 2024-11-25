/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import {
  login,
  save,
  addReportToDashboard,
  createNewReport,
  createNewDashboard,
  bulkDeleteAllItems,
} from '../utils';

// import * as Report from './ProcessReport.elements.js';
// import * as Filter from './Filter.elements.js';
import * as e from './Homepage.elements.js';
import * as Common from './Common.elements.js';

fixture('Homepage')
  .page(config.endpoint)
  .beforeEach(async (t) => {
    await login(t);
    await t.navigateTo(config.collectionsEndpoint);
  })
  .afterEach(cleanEntities);

test('navigate to report view and edit pages', async (t) => {
  await createNewReport(t);
  await save(t);

  await t.click(Common.collectionsPage);

  await t.click(Common.listItemLink('report'));

  await t.expect(e.noDataNotice.textContent).contains('Report configuration is incomplete');

  await t.click(Common.collectionsPage);

  await t.hover(Common.listItem('report'));
  await t.click(Common.contextMenu(Common.listItem('report')));
  await t.click(Common.edit);

  await t.expect(Common.controlPanel.visible).ok();
});

test('navigate to dashboard view and edit pages', async (t) => {
  await createNewDashboard(t);
  await save(t);

  await t.click(Common.collectionsPage);

  await t.click(Common.listItemLink('dashboard'));

  await t.expect(Common.editButton.visible).ok();

  await t.click(Common.collectionsPage);

  await t.hover(Common.listItem('dashboard'));
  await t.click(Common.contextMenu(Common.listItem('dashboard')));
  await t.click(Common.edit);

  await t.expect(Common.addButton.visible).ok();
});

test('complex Homepage actions', async (t) => {
  await t.click(Common.createNewButton);
  const createNewMenu = Common.menu('Create new').textContent;

  await t.expect(createNewMenu).contains('Collection');
  await t.expect(createNewMenu).contains('Dashboard');
  await t.expect(createNewMenu).contains('Report');

  await t.click(Common.menuOption('Report'));
  await t.click(Common.templateModalProcessField);
  await t.click(Common.firstOption);
  await t.click(Common.modalConfirmButton);

  await t.typeText(Common.nameEditField, 'Invoice Evaluation count', {replace: true});
  await save(t);
  await t.click(Common.collectionsPage);

  await t.click(Common.createNewButton);
  await t.click(Common.menuOption('Report'));
  await t.click(Common.templateModalProcessField);
  await t.click(Common.firstOption);
  await t.click(Common.modalConfirmButton);

  await t.typeText(Common.nameEditField, 'Monthly Sales From Marketing', {replace: true});
  await save(t);
  await t.click(Common.collectionsPage);

  await t.expect(Common.listItem('report').visible).ok();
  await t.expect(Common.listItem('report').textContent).contains('Monthly Sales From Marketing');

  await createNewDashboard(t);

  await t.typeText(Common.nameEditField, 'Sales Dashboard', {replace: true});

  await addReportToDashboard(t, 'Monthly Sales From Marketing');
  await addReportToDashboard(t, 'Invoice Evaluation count');
  await addReportToDashboard(t, 'Monthly Sales From Marketing');

  await save(t);
  await t.click(Common.collectionsPage);

  await t.expect(Common.listItem('dashboard').visible).ok();
  await t.expect(Common.listItem('dashboard').textContent).contains('Sales Dashboard');
  await t.expect(Common.listItem('dashboard').textContent).contains('3 Reports');

  // breadcrumb navigation
  await t.click(Common.listItemLink('dashboard'));
  await t.click(e.dashboardReportLink);
  await t.click(e.breadcrumb('Sales Dashboard'));
  await t.expect(e.dashboardView.visible).ok();
  await t.click(Common.collectionsPage);

  await t.click(Common.createNewButton).click(Common.menuOption('Collection'));
  await t.typeText(Common.modalNameInput, 'Marketing', {replace: true});
  await t.click(Common.modalConfirmButton);
  await t.click(Common.modalConfirmButton);

  await createNewDashboard(t);
  await save(t);

  await t.click(Common.collectionsPage);

  await t.click(Common.createNewButton).click(Common.menuOption('Collection'));
  await t.typeText(Common.modalNameInput, 'Sales', {replace: true});
  await t.click(Common.modalConfirmButton);
  await t.click(Common.modalConfirmButton);

  await t.click(Common.createNewButton);
  await t.click(Common.menuOption('Report'));
  await t.click(Common.templateModalProcessField);
  await t.click(Common.firstOption);
  await t.click(Common.modalConfirmButton);

  await t.typeText(Common.nameEditField, 'Incoming Leads', {replace: true});
  await save(t);
  await t.click(e.breadcrumb('Sales'));

  await t.click(Common.createNewButton);
  await t.click(Common.menuOption('Report'));
  await t.click(Common.templateModalProcessField);
  await t.click(Common.firstOption);
  await t.click(Common.modalConfirmButton);
  await t.typeText(Common.nameEditField, 'Sales Goal this Quarter', {replace: true});
  await save(t);
  await t.click(e.breadcrumb('Sales'));

  await createNewDashboard(t);

  await t.typeText(Common.nameEditField, 'Sales Dashboard', {replace: true});
  await addReportToDashboard(t, 'Incoming Leads');
  await addReportToDashboard(t, 'Sales Goal this Quarter');
  await save(t);
  await t.click(e.breadcrumb('Sales'));

  await t
    .resizeWindow(1150, 550)
    .takeScreenshot('img/collection.png', {fullPage: true})
    .maximizeWindow();

  await t.click(Common.collectionsPage);

  await t.expect(Common.listItem('collection').visible).ok();
  await t.expect(Common.listItem('collection').textContent).contains('Sales');
  await t.expect(Common.listItem('collection').textContent).contains('1 Dashboard, 2 Reports');

  await t.takeElementScreenshot(Common.entityList, 'img/home.png');

  // search
  await t.typeText(e.searchField, 'sales', {replace: true});
  await t.expect(Common.listItem('collection').visible).ok();
  await t.expect(Common.listItem('dashboard').visible).ok();
  await t.expect(Common.listItem('report').visible).ok();

  await t.typeText(e.searchField, 'sales d', {replace: true});
  await t.expect(Common.listItem('collection').exists).notOk();
  await t.expect(Common.listItem('dashboard').visible).ok();
  await t.expect(Common.listItem('report').exists).notOk();

  await t.typeText(e.searchField, 'marketing', {replace: true});
  await t.expect(Common.listItem('collection').visible).ok();
  await t.expect(Common.listItem('dashboard').exists).notOk();
  await t.expect(Common.listItem('report').visible).ok();

  await t.typeText(e.searchField, 'Collection b', {replace: true});
  await t.expect(Common.listItem('collection').exists).notOk();
  await t.expect(Common.listItem('dashboard').exists).notOk();
  await t.expect(Common.listItem('report').exists).notOk();

  await t.click(e.searchField).pressKey('ctrl+a delete');
  // copy to new location
  await t.hover(Common.listItem('dashboard'));
  await t.click(Common.contextMenu(Common.listItem('dashboard')));
  await t.click(Common.copy);

  await t.click(Common.toggleElement('Move copy to'));
  await t.click(e.copyTargetsInput);
  await t.click(Common.carbonOption('Sales'));

  await t.takeElementScreenshot(Common.modalContainer, 'img/copy.png');

  await t.click(Common.confirmButton);

  await t.expect(Common.listItem('dashboard').visible).ok();
  await t.expect(Common.listItem('dashboard').textContent).contains('Sales Dashboard (copy)');

  await t.expect(Common.listItem('report').visible).ok();
  await t.expect(Common.listItem('report').textContent).contains('Invoice Evaluation count – Copy');

  // bulk deleting home entities
  await bulkDeleteAllItems(t);
  await t.expect(Common.listItem.exists).notOk();
});

test('multi definition selection', async (t) => {
  await t.click(Common.createNewButton);
  await t.click(Common.menuOption('Report'));

  const firstDefinition = 'Order process';
  await t
    .typeText(Common.templateModalProcessField, firstDefinition, {replace: true})
    .click(Common.carbonOption(firstDefinition));

  const secondDefinition = 'complexProcess';
  await t
    .typeText(Common.templateModalProcessField, secondDefinition, {replace: true})
    .click(Common.carbonOption(secondDefinition));

  await t.click(Common.modalConfirmButton);
});

test('create new dashboard from an empty state component', async (t) => {
  await t.expect(e.emptyStateComponent.visible).ok();

  await t.click(e.createNewDashboardButton);
  await t.click(e.blankDashboardButton);

  await t.click(Common.modalConfirmButton);

  await save(t);

  await t.click(Common.collectionsPage);
  await t.expect(e.emptyStateComponent.exists).notOk();

  await bulkDeleteAllItems(t);

  await t.expect(e.emptyStateComponent.visible).ok();
});

// test('create new KPI report', async (t) => {
//   await t.click(Common.createNewButton);
//   await t.click(Common.menuOption('Process KPI'));

//   await t.click(Common.kpiTemplateSelection);
//   await t.click(Common.carbonOption('Automation rate'));

//   await t.click(Common.templateModalProcessField);
//   await t.click(Common.carbonOption('Order process'));
//   await t
//     .resizeWindow(1000, 750)
//     .takeElementScreenshot(Common.modalContainer, 'img/process-kpi-step1.png')
//     .maximizeWindow();
//   await t.click(Common.modalConfirmButton);

//   await t.click(Common.kpiFilterButton);

//   await t.click(Report.flowNode('approveInvoice'));
//   await t.click(Report.flowNode('reviewInvoice'));
//   await t.click(Report.flowNode('prepareBankTransfer'));
//   await t.click(Common.modalConfirmButton);

//   await t.click(Common.kpiFilterButton);

//   await t.click(Filter.dateTypeSelect);
//   await t.click(Common.menuOption('This...'));
//   await t.click(Filter.unitSelect);
//   await t.click(Common.menuOption('year'));
//   await t.click(Common.modalConfirmButton);
//   await t.takeElementScreenshot(Common.modalContainer, 'img/process-kpi-step2.png');

//   await t.click(Common.modalConfirmButton);
//   await t.expect(Common.editButton.visible).ok();
// });
