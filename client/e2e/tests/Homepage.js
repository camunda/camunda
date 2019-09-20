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
