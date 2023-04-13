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
import * as Common from './Common.elements.js';

fixture('Homepage').page(config.endpoint).beforeEach(login).afterEach(cleanEntities);

test('navigate to report view and edit pages', async (t) => {
  await createNewReport(t);
  await save(t);

  await t.click(e.homepageLink);

  await t.click(Common.reportItem);

  await t.expect(e.noDataNotice.textContent).contains('Report configuration is incomplete');

  await t.click(e.homepageLink);

  await t.hover(Common.reportItem);
  await t.click(Common.contextMenu(Common.reportItem));
  await t.click(Common.edit(Common.reportItem));

  await t.expect(Common.controlPanel.visible).ok();
});

test('navigate to dashboard view and edit pages', async (t) => {
  await createNewDashboard(t);
  await save(t);

  await t.click(e.homepageLink);

  await t.click(Common.dashboardItem);

  await t.expect(Common.editButton.visible).ok();

  await t.click(e.homepageLink);

  await t.hover(Common.dashboardItem);
  await t.click(Common.contextMenu(Common.dashboardItem));
  await t.click(Common.edit(Common.dashboardItem));

  await t.expect(Common.addButton.visible).ok();
});

test('complex Homepage actions', async (t) => {
  await t.click(Common.createNewMenu).hover(Common.newReportOption);

  await t.expect(Common.createNewMenu.textContent).contains('Collection');
  await t.expect(Common.createNewMenu.textContent).contains('Dashboard');
  await t.expect(Common.createNewMenu.textContent).contains('Process Report');
  await t.expect(Common.createNewMenu.textContent).contains('Combined Process Report');
  await t.expect(Common.createNewMenu.textContent).contains('Decision Report');

  await t.click(Common.submenuOption('Process Report'));
  await t.click(Common.templateModalProcessField);
  await t.click(Common.firstTypeaheadOption);
  await t.click(Common.carbonModalConfirmBtn);

  await t.typeText(Common.nameEditField, 'Invoice Evaluation Count', {replace: true});
  await save(t);
  await t.click(e.homepageLink);

  await t.click(Common.createNewMenu).hover(Common.newReportOption);
  await t.click(Common.submenuOption('Process Report'));
  await t.click(Common.templateModalProcessField);
  await t.click(Common.firstTypeaheadOption);
  await t.click(Common.carbonModalConfirmBtn);

  await t.typeText(Common.nameEditField, 'Monthly Sales From Marketing', {replace: true});
  await save(t);
  await t.click(e.homepageLink);

  await t.expect(Common.reportItem.visible).ok();
  await t.expect(Common.reportItem.textContent).contains('Monthly Sales From Marketing');

  await createNewDashboard(t);

  await t.typeText(Common.nameEditField, 'Sales Dashboard', {replace: true});

  await addReportToDashboard(t, 'Monthly Sales From Marketing');
  await addReportToDashboard(t, 'Invoice Evaluation Count');
  await addReportToDashboard(t, 'Monthly Sales From Marketing');

  await save(t);
  await t.click(e.homepageLink);

  await t.expect(Common.dashboardItem.visible).ok();
  await t.expect(Common.dashboardItem.textContent).contains('Sales Dashboard');
  await t.expect(Common.dashboardItem.textContent).contains('3 Reports');

  // breadcrumb navigation
  await t.click(Common.dashboardItem);
  await t.click(e.dashboardReportLink);
  await t.click(e.breadcrumb('Sales Dashboard'));

  await t.expect(e.dashboardView.visible).ok();

  await t.click(e.homepageLink);

  await t.click(Common.createNewMenu).click(Common.option('Collection'));
  await t.typeText(Common.modalNameInput, 'Marketing', {replace: true});
  await t.click(Common.carbonModalConfirmBtn);
  await t.click(Common.carbonModalConfirmBtn);

  await createNewDashboard(t);
  await save(t);

  await t.click(e.homepageLink);

  await t.click(Common.createNewMenu).click(Common.option('Collection'));
  await t.typeText(Common.modalNameInput, 'Sales', {replace: true});
  await t.click(Common.carbonModalConfirmBtn);
  await t.click(Common.carbonModalConfirmBtn);

  await t.click(Common.createNewMenu).hover(Common.newReportOption);
  await t.click(Common.submenuOption('Process Report'));
  await t.click(Common.templateModalProcessField);
  await t.click(Common.firstTypeaheadOption);
  await t.click(Common.carbonModalConfirmBtn);

  await t.typeText(Common.nameEditField, 'Incoming Leads', {replace: true});
  await save(t);
  await t.click(e.breadcrumb('Sales'));

  await t.click(Common.createNewMenu).hover(Common.newReportOption);
  await t.click(Common.submenuOption('Process Report'));
  await t.click(Common.templateModalProcessField);
  await t.click(Common.firstTypeaheadOption);
  await t.click(Common.carbonModalConfirmBtn);

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

  await t.click(e.homepageLink);

  await t.expect(Common.collectionItem.visible).ok();
  await t.expect(Common.collectionItem.textContent).contains('Sales');
  await t.expect(Common.collectionItem.textContent).contains('1 Dashboard, 2 Reports');

  await t.takeElementScreenshot(Common.entityList, 'img/home.png');

  // search
  await t.click(e.searchButton);
  await t.typeText(e.searchField, 'sales', {replace: true});
  await t.expect(Common.collectionItem.visible).ok();
  await t.expect(Common.dashboardItem.visible).ok();
  await t.expect(Common.reportItem.visible).ok();

  await t.typeText(e.searchField, 'sales d', {replace: true});
  await t.expect(Common.collectionItem.exists).notOk();
  await t.expect(Common.dashboardItem.visible).ok();
  await t.expect(Common.reportItem.exists).notOk();

  await t.typeText(e.searchField, 'marketing', {replace: true});
  await t.expect(Common.collectionItem.visible).ok();
  await t.expect(Common.dashboardItem.exists).notOk();
  await t.expect(Common.reportItem.visible).ok();

  await t.typeText(e.searchField, 'Collection b', {replace: true});
  await t.expect(Common.collectionItem.exists).notOk();
  await t.expect(Common.dashboardItem.exists).notOk();
  await t.expect(Common.reportItem.exists).notOk();

  await t.click(e.searchField).pressKey('ctrl+a delete');
  // copy to new location
  await t.hover(Common.dashboardItem);
  await t.click(Common.contextMenu(Common.dashboardItem));
  await t.click(Common.copy(Common.dashboardItem));

  await t.click(e.moveCopySwitch);
  await t.click(e.copyTargetsInput);
  await t.click(e.copyTarget('Sales'));

  await t.takeElementScreenshot(e.copyModal, 'img/copy.png');

  await t.click(Common.confirmButton);

  await t.expect(Common.dashboardItem.visible).ok();
  await t.expect(Common.dashboardItem.textContent).contains('Sales Dashboard (copy)');

  await t.expect(Common.reportItem.visible).ok();
  await t.expect(Common.reportItem.textContent).contains('Invoice Evaluation Count – Copy');

  // bulk deleting home entities
  await bulkDeleteAllItems(t);
  await t.expect(Common.listItem.exists).notOk();
});

test('multi definition selection', async (t) => {
  await t.click(Common.createNewMenu);
  await t.click(Common.newReportOption);
  await t.click(Common.submenuOption('Process Report'));

  const firstDefinition = 'Invoice Receipt with alternative correlation variable';
  await t
    .click(e.definitionSelection)
    .typeText(Common.templateModalProcessField, firstDefinition, {replace: true})
    .click(Common.option(firstDefinition));

  const secondDefinition = 'Embedded Subprocess';
  await t
    .click(e.definitionSelection)
    .typeText(Common.templateModalProcessField, secondDefinition, {replace: true})
    .click(Common.option(secondDefinition));

  await t.click(Common.carbonModalConfirmBtn);
});

test('create new dashboard from an empty state component', async (t) => {
  await t.expect(e.emptyStateComponent.visible).ok();

  await t.click(e.createNewDashboardButton);
  await t.click(e.blankDashboardButton);

  await t.click(Common.carbonModalConfirmBtn);

  await save(t);

  await t.click(e.homepageLink);
  await t.expect(e.emptyStateComponent.exists).notOk();

  await bulkDeleteAllItems(t);

  await t.expect(e.emptyStateComponent.visible).ok();
});
