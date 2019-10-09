/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import {login, save, addReportToDashboard} from '../utils';

import * as e from './Homepage.elements.js';

fixture('Homepage')
  .page(config.endpoint)
  .before(setup);

test('create a report and show it on the Homepage', async t => {
  await login(t);

  await t.click(e.createNewMenu).hover(e.newReportOption);

  await t.expect(e.createNewMenu.textContent).contains('New Collection');
  await t.expect(e.createNewMenu.textContent).contains('New Dashboard');
  await t.expect(e.createNewMenu.textContent).contains('Process Report');
  await t.expect(e.createNewMenu.textContent).contains('Combined Process Report');
  await t.expect(e.createNewMenu.textContent).contains('Decision Report');

  await t.click(e.submenuOption('Process Report'));
  await save(t);
  await t.click(e.homepageLink);

  await t.expect(e.reportItem.visible).ok();
  await t.expect(e.reportItem.textContent).contains('New Report');
});

test('create a dashboard and show it on the homepage', async t => {
  await login(t);

  await t.click(e.createNewMenu).click(e.option('New Dashboard'));
  await save(t);
  await t.click(e.homepageLink);

  await t.expect(e.dashboardItem.visible).ok();
  await t.expect(e.dashboardItem.textContent).contains('New Dashboard');
});

test('navigate to report view and edit pages', async t => {
  await login(t);

  await t.click(e.reportItem);

  await t.expect(e.setupNotice.visible).ok();
  await t.expect(e.setupNotice.textContent).contains('Select the Edit button above');

  await t.click(e.homepageLink);

  await t.hover(e.reportItem);
  await t.click(e.contextMenu(e.reportItem));
  await t.click(e.edit(e.reportItem));

  await t.expect(e.reportControlPanel.visible).ok();
});

test('navigate to dashboard view and edit pages', async t => {
  await login(t);
  await t.click(e.dashboardItem);

  await t.expect(e.editButton.visible).ok();

  await t.click(e.homepageLink);

  await t.hover(e.dashboardItem);
  await t.click(e.contextMenu(e.dashboardItem));
  await t.click(e.edit(e.dashboardItem));

  await t.expect(e.addButton.visible).ok();
});

test('use breadcrumbs to navigate back from a report to the parent dashboard', async t => {
  await login(t);

  await t.hover(e.dashboardItem);
  await t.click(e.contextMenu(e.dashboardItem));
  await t.click(e.edit(e.dashboardItem));

  await addReportToDashboard(t, 'New Report');
  await save(t);

  await t.click(e.dashboardReportLink);
  await t.click(e.dashboardBreadcrumb);

  await t.expect(e.dashboardView.visible).ok();
});

test('create a new collection and display it on the homepage', async t => {
  await login(t);

  await t.click(e.createNewMenu).click(e.option('New Collection'));
  await t.typeText(e.modalNameInput, 'Test Collection', {replace: true});
  await t.click(e.confirmButton);

  await t.click(e.homepageLink);

  await t.expect(e.collectionItem.visible).ok();
  await t.expect(e.collectionItem.textContent).contains('Test Collection');
});

test('filter for the name entered', async t => {
  await login(t);

  await t.typeText(e.searchField, 'new', {replace: true});
  await t.expect(e.collectionItem.exists).notOk();
  await t.expect(e.dashboardItem.visible).ok();
  await t.expect(e.reportItem.visible).ok();

  await t.typeText(e.searchField, 't', {replace: true});
  await t.expect(e.collectionItem.visible).ok();
  await t.expect(e.dashboardItem.exists).notOk();
  await t.expect(e.reportItem.visible).ok();

  await t.typeText(e.searchField, 'Test Collection', {replace: true});
  await t.expect(e.collectionItem.visible).ok();
  await t.expect(e.dashboardItem.exists).notOk();
  await t.expect(e.reportItem.exists).notOk();

  await t.typeText(e.searchField, 'Collection b', {replace: true});
  await t.expect(e.collectionItem.exists).notOk();
  await t.expect(e.dashboardItem.exists).notOk();
  await t.expect(e.reportItem.exists).notOk();
});

test('copy to a new location', async t => {
  await login(t);

  await t.hover(e.dashboardItem);
  await t.click(e.contextMenu(e.dashboardItem));
  await t.click(e.copy(e.dashboardItem));

  await t.typeText(e.modalNameInput, 'Copy of the Dashboard', {replace: true});

  await t.click(e.moveCopySwitch);
  await t.click(e.copyTargetsInput);
  await t.click(e.copyTarget('Test Collection'));
  await t.click(e.confirmButton);

  await t.expect(e.dashboardItem.visible).ok();
  await t.expect(e.dashboardItem.textContent).contains('Copy of the Dashboard');

  await t.expect(e.reportItem.visible).ok();
  await t.expect(e.reportItem.textContent).contains('New Report – Copy');
});
