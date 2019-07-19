/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import {login, save} from '../utils';

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

  await t.click(e.createDashboardButton);
  await save(t);
  await t.click(e.homepageLink);

  await t.expect(e.dashboardItem.visible).ok();
  await t.expect(e.dashboardItem.textContent).contains('New Dashboard');
});

test('only show the first five reports unless show all is clicked', async t => {
  await login(t);

  for (let i = 0; i < 10; i++) {
    await t.hover(e.reportItem);
    await t.click(e.duplicate('Report'));
  }

  await t.expect(e.reportItem.count).eql(5);
  await t.expect(e.showAll('Reports').visible).ok();

  await t.click(e.showAll('Reports'));

  await t.expect(e.reportItem.count).eql(11);
});

test('navigate to report view and edit pages', async t => {
  await login(t);

  await t.click(e.reportItem);

  await t.expect(e.setupNotice.visible).ok();
  await t.expect(e.setupNotice.textContent).contains('Select the Edit button above');

  await t.click(e.homepageLink);

  await t.hover(e.reportItem);
  await t.click(e.edit('Report'));

  await t.expect(e.reportControlPanel.visible).ok();
});

test('only show the first five dashboards unless show all is clicked', async t => {
  await login(t);

  for (let i = 0; i < 10; i++) {
    await t.hover(e.dashboardItem);
    await t.click(e.duplicate('Dashboard'));
  }

  await t.expect(e.dashboardItem.count).eql(5);
  await t.expect(e.showAll('Dashboards').visible).ok();

  await t.click(e.showAll('Dashboards'));

  await t.expect(e.dashboardItem.count).eql(11);
});

test('navigate to dashboard view and edit pages', async t => {
  await login(t);
  await t.click(e.dashboardItem);

  await t.expect(e.editButton.visible).ok();

  await t.click(e.homepageLink);

  await t.hover(e.dashboardItem);
  await t.click(e.edit('Dashboard'));

  await t.expect(e.addButton.visible).ok();
});

test('create collections and add items to it', async t => {
  await login(t);

  await t.click(e.createNewMenu);
  await t.click(e.option('New Collection'));
  await t.click(e.createCollectionButton);

  await t.expect(e.collectionItem.visible).ok();

  await t.click(e.collectionsDropdownFor('Dashboard'));
  await t.click(e.collectionOption('New Collection'));

  await t.expect(e.dashboardItem.textContent).contains('In 1 Collection');

  await t.click(e.collectionsDropdownFor('Report'));
  await t.click(e.collectionOption('New Collection'));

  await t.expect(e.reportItem.textContent).contains('In 1 Collection');

  await t.expect(e.collectionItem.textContent).contains('2 Items');

  await t.expect(e.dashboardInCollection.exists).notOk();
  await t.click(e.collectionItem);
  await t.expect(e.dashboardInCollection.visible).ok();
});
