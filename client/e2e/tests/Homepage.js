/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

import * as e from './Homepage.elements.js';
import * as Report from './ProcessReport.elements.js';
import * as Dashboard from './Dashboard.elements.js';

fixture('Homepage').page(config.endpoint).beforeEach(login).afterEach(cleanEntities);

test('navigate to report view and edit pages', async (t) => {
  await createNewReport(t);
  await save(t);

  await t.click(e.homepageLink);

  await t.click(e.reportItem);

  await t.expect(e.noDataNotice.textContent).contains('Report configuration is incomplete');

  await t.click(e.homepageLink);

  await t.hover(e.reportItem);
  await t.click(e.contextMenu(e.reportItem));
  await t.click(e.edit(e.reportItem));

  await t.expect(e.reportControlPanel.visible).ok();
});

test('navigate to dashboard view and edit pages', async (t) => {
  await createNewDashboard(t);
  await save(t);

  await t.click(e.homepageLink);

  await t.click(e.dashboardItem);

  await t.expect(e.editButton.visible).ok();

  await t.click(e.homepageLink);

  await t.hover(e.dashboardItem);
  await t.click(e.contextMenu(e.dashboardItem));
  await t.click(e.edit(e.dashboardItem));

  await t.expect(e.addButton.visible).ok();
});

test('complex Homepage actions', async (t) => {
  await t.click(e.createNewMenu).hover(e.newReportOption);

  await t.expect(e.createNewMenu.textContent).contains('Collection');
  await t.expect(e.createNewMenu.textContent).contains('Dashboard');
  await t.expect(e.createNewMenu.textContent).contains('Process Report');
  await t.expect(e.createNewMenu.textContent).contains('Combined Process Report');
  await t.expect(e.createNewMenu.textContent).contains('Decision Report');

  await t.click(e.submenuOption('Process Report'));
  await t.click(e.templateModalProcessField);
  await t.click(e.firstTypeaheadOption);
  await t.click(e.carbonModalConfirmBtn);

  await t.typeText(Report.nameEditField, 'Invoice Evaluation Count', {replace: true});
  await save(t);
  await t.click(e.homepageLink);

  await t.click(e.createNewMenu).hover(e.newReportOption);
  await t.click(e.submenuOption('Process Report'));
  await t.click(e.templateModalProcessField);
  await t.click(e.firstTypeaheadOption);
  await t.click(e.carbonModalConfirmBtn);

  await t.typeText(Report.nameEditField, 'Monthly Sales From Marketing', {replace: true});
  await save(t);
  await t.click(e.homepageLink);

  await t.expect(e.reportItem.visible).ok();
  await t.expect(e.reportItem.textContent).contains('Monthly Sales From Marketing');

  await createNewDashboard(t);

  await t.typeText(Dashboard.nameEditField, 'Sales Dashboard', {replace: true});

  await addReportToDashboard(t, 'Monthly Sales From Marketing');
  await addReportToDashboard(t, 'Invoice Evaluation Count');
  await addReportToDashboard(t, 'Monthly Sales From Marketing');

  await save(t);
  await t.click(e.homepageLink);

  await t.expect(e.dashboardItem.visible).ok();
  await t.expect(e.dashboardItem.textContent).contains('Sales Dashboard');
  await t.expect(e.dashboardItem.textContent).contains('3 Reports');

  // breadcrumb navigation
  await t.click(e.dashboardItem);
  await t.click(e.dashboardReportLink);
  await t.click(e.breadcrumb('Sales Dashboard'));

  await t.expect(e.dashboardView.visible).ok();

  await t.click(e.homepageLink);

  await t.click(e.createNewMenu).click(e.option('Collection'));
  await t.typeText(e.modalNameInput, 'Marketing', {replace: true});
  await t.click(e.carbonModalConfirmBtn);
  await t.click(e.carbonModalConfirmBtn);

  await createNewDashboard(t);
  await save(t);

  await t.click(e.homepageLink);

  await t.click(e.createNewMenu).click(e.option('Collection'));
  await t.typeText(e.modalNameInput, 'Sales', {replace: true});
  await t.click(e.carbonModalConfirmBtn);
  await t.click(e.carbonModalConfirmBtn);

  await t.click(e.createNewMenu).hover(e.newReportOption);
  await t.click(e.submenuOption('Process Report'));
  await t.click(e.templateModalProcessField);
  await t.click(e.firstTypeaheadOption);
  await t.click(e.carbonModalConfirmBtn);

  await t.typeText(Report.nameEditField, 'Incoming Leads', {replace: true});
  await save(t);
  await t.click(e.breadcrumb('Sales'));

  await t.click(e.createNewMenu).hover(e.newReportOption);
  await t.click(e.submenuOption('Process Report'));
  await t.click(e.templateModalProcessField);
  await t.click(e.firstTypeaheadOption);
  await t.click(e.carbonModalConfirmBtn);

  await t.typeText(Report.nameEditField, 'Sales Goal this Quarter', {replace: true});
  await save(t);
  await t.click(e.breadcrumb('Sales'));

  await createNewDashboard(t);

  await t.typeText(Dashboard.nameEditField, 'Sales Dashboard', {replace: true});
  await addReportToDashboard(t, 'Incoming Leads');
  await addReportToDashboard(t, 'Sales Goal this Quarter');
  await save(t);
  await t.click(e.breadcrumb('Sales'));

  await t
    .resizeWindow(1150, 550)
    .takeScreenshot('img/collection.png', {fullPage: true})
    .maximizeWindow();

  await t.click(e.homepageLink);

  await t.expect(e.collectionItem.visible).ok();
  await t.expect(e.collectionItem.textContent).contains('Sales');
  await t.expect(e.collectionItem.textContent).contains('1 Dashboard, 2 Reports');

  await t.takeElementScreenshot(e.entityList, 'img/home.png');

  // search
  await t.click(e.searchButton);
  await t.typeText(e.searchField, 'sales', {replace: true});
  await t.expect(e.collectionItem.visible).ok();
  await t.expect(e.dashboardItem.visible).ok();
  await t.expect(e.reportItem.visible).ok();

  await t.typeText(e.searchField, 'sales d', {replace: true});
  await t.expect(e.collectionItem.exists).notOk();
  await t.expect(e.dashboardItem.visible).ok();
  await t.expect(e.reportItem.exists).notOk();

  await t.typeText(e.searchField, 'marketing', {replace: true});
  await t.expect(e.collectionItem.visible).ok();
  await t.expect(e.dashboardItem.exists).notOk();
  await t.expect(e.reportItem.visible).ok();

  await t.typeText(e.searchField, 'Collection b', {replace: true});
  await t.expect(e.collectionItem.exists).notOk();
  await t.expect(e.dashboardItem.exists).notOk();
  await t.expect(e.reportItem.exists).notOk();

  await t.click(e.searchField).pressKey('ctrl+a delete');
  // copy to new location
  await t.hover(e.dashboardItem);
  await t.click(e.contextMenu(e.dashboardItem));
  await t.click(e.copy(e.dashboardItem));

  await t.click(e.moveCopySwitch);
  await t.click(e.copyTargetsInput);
  await t.click(e.copyTarget('Sales'));

  await t.takeElementScreenshot(e.copyModal, 'img/copy.png');

  await t.click(e.confirmButton);

  await t.expect(e.dashboardItem.visible).ok();
  await t.expect(e.dashboardItem.textContent).contains('Sales Dashboard (copy)');

  await t.expect(e.reportItem.visible).ok();
  await t.expect(e.reportItem.textContent).contains('Invoice Evaluation Count – Copy');

  // bulk deleting home entities
  await bulkDeleteAllItems(t);
  await t.expect(e.listItem.exists).notOk();
});

test('multi definition selection', async (t) => {
  await t.click(e.createNewMenu);
  await t.click(e.newReportOption);
  await t.click(e.submenuOption('Process Report'));

  const firstDefinition = 'Invoice Receipt with alternative correlation variable';
  await t
    .click(e.definitionSelection)
    .typeText(e.templateModalProcessField, firstDefinition, {replace: true})
    .click(e.option(firstDefinition));

  const secondDefinition = 'Embedded Subprocess';
  await t
    .click(e.definitionSelection)
    .typeText(e.templateModalProcessField, secondDefinition, {replace: true})
    .click(e.option(secondDefinition));

  await t.click(e.carbonModalConfirmBtn);
});

test('create new dashboard from an empty state component', async (t) => {
  await t.expect(e.emptyStateComponent.visible).ok();

  await t.click(e.createNewDashboardButton);
  await t.click(e.blankDashboardButton);

  await t.click(e.carbonModalConfirmBtn);

  await save(t);

  await t.click(e.homepageLink);
  await t.expect(e.emptyStateComponent.exists).notOk();

  await bulkDeleteAllItems(t);

  await t.expect(e.emptyStateComponent.visible).ok();
});
