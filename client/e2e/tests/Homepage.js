/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import {login, save, addReportToDashboard} from '../utils';

import * as e from './Homepage.elements.js';
import * as Report from './ProcessReport.elements.js';
import * as Dashboard from './Dashboard.elements.js';
import * as Collection from './Collection.elements.js';

fixture('Homepage').page(config.endpoint).beforeEach(login).afterEach(cleanEntities);

test('navigate to report view and edit pages', async (t) => {
  await t.click(e.createNewMenu).hover(e.newReportOption);
  await t.click(e.submenuOption('Process Report'));

  await t.click(e.processTypeahead);
  await t.click(e.firstTypeaheadOption);
  await t.click(e.confirmButton);

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
  await t.click(e.createNewMenu).click(e.option('New Dashboard'));
  await t.click(e.modalConfirmbutton);

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

  await t.expect(e.createNewMenu.textContent).contains('New Collection');
  await t.expect(e.createNewMenu.textContent).contains('New Dashboard');
  await t.expect(e.createNewMenu.textContent).contains('Process Report');
  await t.expect(e.createNewMenu.textContent).contains('Combined Process Report');
  await t.expect(e.createNewMenu.textContent).contains('Decision Report');

  await t.click(e.submenuOption('Process Report'));
  await t.click(e.processTypeahead);
  await t.click(e.firstTypeaheadOption);
  await t.click(e.confirmButton);

  await t.typeText(Report.nameEditField, 'Invoice Evaluation Count', {replace: true});
  await save(t);
  await t.click(e.homepageLink);

  await t.click(e.createNewMenu).hover(e.newReportOption);
  await t.click(e.submenuOption('Process Report'));
  await t.click(e.processTypeahead);
  await t.click(e.firstTypeaheadOption);
  await t.click(e.confirmButton);

  await t.typeText(Report.nameEditField, 'Monthly Sales From Marketing', {replace: true});
  await save(t);
  await t.click(e.homepageLink);

  await t.expect(e.reportItem.visible).ok();
  await t.expect(e.reportItem.textContent).contains('Monthly Sales From Marketing');

  await t.click(e.createNewMenu).click(e.option('New Dashboard'));
  await t.click(e.modalConfirmbutton);

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

  await t.click(e.createNewMenu).click(e.option('New Collection'));
  await t.typeText(e.modalNameInput, 'Marketing', {replace: true});
  await t.click(e.confirmButton);

  await t.click(Collection.sourcesTab);

  await t.click(Collection.addButton);
  const collectionDefinitionName = 'Invoice Receipt with alternative correlation variable';
  await t.typeText(Collection.typeaheadInput, collectionDefinitionName, {replace: true});
  await t.click(Collection.typeaheadOption(collectionDefinitionName));
  await t.click(Collection.checkbox('Select All'));
  await t.click(Collection.confirmModalButton);

  await t.click(Collection.entitiesTab);

  await t.click(e.createNewMenu).click(e.option('New Dashboard'));
  await t.click(e.modalConfirmbutton);

  await save(t);

  await t.click(e.breadcrumb('Marketing'));
  await t.click(Collection.sourcesTab);
  await t.click(Collection.addButton);
  await t.click(e.processTypeahead);
  await t.click(e.firstTypeaheadOption);
  await t.click(Collection.checkbox('Select All'));
  await t.click(Collection.confirmModalButton);

  await t.click(e.homepageLink);

  await t.click(e.createNewMenu).click(e.option('New Collection'));
  await t.typeText(e.modalNameInput, 'Sales', {replace: true});
  await t.click(e.confirmButton);

  await t.click(Collection.sourcesTab);
  await t.click(Collection.addButton);
  const definitionName = 'Hiring Demo 5 Tenants';
  await t.typeText(Collection.typeaheadInput, definitionName, {replace: true});
  await t.click(Collection.typeaheadOption(definitionName));
  await t.click(Collection.checkbox('Select All'));
  await t.click(Collection.confirmModalButton);
  await t.click(Collection.entitiesTab);

  await t.click(e.createNewMenu).hover(e.newReportOption);
  await t.click(e.submenuOption('Process Report'));
  await t.click(e.processTypeahead);
  await t.click(e.firstTypeaheadOption);
  await t.click(e.confirmButton);

  await t.typeText(Report.nameEditField, 'Incoming Leads', {replace: true});
  await save(t);
  await t.click(e.breadcrumb('Sales'));

  await t.click(e.createNewMenu).hover(e.newReportOption);
  await t.click(e.submenuOption('Process Report'));
  await t.click(e.processTypeahead);
  await t.click(e.firstTypeaheadOption);
  await t.click(e.confirmButton);

  await t.typeText(Report.nameEditField, 'Sales Goal this Quarter', {replace: true});
  await save(t);
  await t.click(e.breadcrumb('Sales'));

  await t.click(e.createNewMenu).click(e.option('New Dashboard'));

  await t.click(e.modalConfirmbutton);

  await t.typeText(Dashboard.nameEditField, 'Sales Dashboard', {replace: true});
  await addReportToDashboard(t, 'Incoming Leads');
  await addReportToDashboard(t, 'Sales Goal this Quarter');
  await save(t);
  await t.click(e.breadcrumb('Sales'));

  await t
    .resizeWindow(1150, 550)
    .takeScreenshot('homepage/collection.png', {fullPage: true})
    .maximizeWindow();

  await t.click(e.homepageLink);

  await t.expect(e.collectionItem.visible).ok();
  await t.expect(e.collectionItem.textContent).contains('Sales');
  await t.expect(e.collectionItem.textContent).contains('1 Dashboard, 2 Reports');

  await t.takeElementScreenshot(e.entityList, 'homepage/home.png');

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

  await t.takeElementScreenshot(e.copyModal, 'homepage/copy.png');

  await t.click(e.confirmButton);

  await t.expect(e.dashboardItem.visible).ok();
  await t.expect(e.dashboardItem.textContent).contains('Sales Dashboard (copy)');

  await t.expect(e.reportItem.visible).ok();
  await t.expect(e.reportItem.textContent).contains('Invoice Evaluation Count – Copy');
});
