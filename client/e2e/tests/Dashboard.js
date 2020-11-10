/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';

import * as e from './Dashboard.elements.js';
import * as Homepage from './Homepage.elements.js';
import * as Filter from './Filter.elements.js';

fixture('Dashboard').page(config.endpoint).beforeEach(u.login).afterEach(cleanEntities);

test('create a dashboard and reports from a template', async (t) => {
  await t.resizeWindow(1300, 750);
  await t.click(Homepage.createNewMenu);
  await t.click(Homepage.option('New Dashboard'));

  await t.typeText(e.templateModalNameField, 'Dashboard from Template', {replace: true});
  await t.click(e.templateOption('Process performance overview'));

  await t.click(e.templateModalProcessField);
  await t.click(e.option('Invoice Receipt with alternative correlation variable'));

  await t.takeScreenshot('dashboard/dashboardTemplate.png', {fullPage: true});
  await t.maximizeWindow();

  await t.click(e.modalConfirmbutton);

  await u.save(t);

  await t.expect(e.dashboardName.textContent).eql('Dashboard from Template');
  await t.expect(e.reportTile.nth(0).textContent).contains('Total Instances');
  await t.expect(e.reportTile.nth(1).textContent).contains('AVG Instance Duration');

  await u.gotoOverview(t);

  await t.expect(Homepage.reportItem.visible).ok();
  await t.expect(Homepage.dashboardItem.visible).ok();
});

test('create a report and add it to the Dashboard', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');
  await u.save(t);
  await u.gotoOverview(t);
  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'New Report');

  await u.save(t);

  await t.expect(e.report.visible).ok();
  await t.expect(e.report.textContent).contains('invoice');
  await t.expect(e.report.textContent).contains('Start Date');
});

test('renaming a dashboard', async (t) => {
  await u.createNewDashboard(t);
  await t.typeText(e.nameEditField, 'New Name', {replace: true});

  await u.save(t);

  await t.expect(e.dashboardName.textContent).eql('New Name');
});

test('cancel changes', async (t) => {
  await u.createNewDashboard(t);
  await u.save(t);

  await t.click(e.editButton);
  await t.typeText(e.nameEditField, 'New Name', {replace: true});
  await u.cancel(t);

  await t.expect(e.dashboardName.textContent).notEql('New Name');
});

// enable this test once https://github.com/DevExpress/testcafe/issues/2863 is fixed
// test('view in fullscreen and dark mode', async t => {
//   await t.click(e.dashboard);
//   await t.click(e.fullscreenButton);

//   await t.expect(e.header.exists).notOk();
//   await t.expect(e.themeButton.visible).ok();

//   await t.click(e.themeButton);

//   await t.expect(e.fullscreenContent.getStyleProperty('background-color')).eql('#222');
// });

test('sharing', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');
  await u.save(t);
  await u.gotoOverview(t);
  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'New Report');

  await u.save(t);

  await t.expect(e.shareButton.hasAttribute('disabled')).notOk();

  await t.click(e.shareButton);
  await t.click(e.shareSwitch);

  const shareUrl = await e.shareUrl.value;

  await t.navigateTo(shareUrl);

  await t.expect(e.report.visible).ok();
  await t.expect(e.report.textContent).contains('invoice');
  await t.expect(e.report.textContent).contains('Start Date');
});

test('sharing with filters', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');
  await u.save(t);
  await u.gotoOverview(t);
  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'New Report');

  await t.click(e.addFilterButton);
  await t.click(e.option('Instance State'));

  await u.save(t);

  await t.click(e.instanceStateFilter);
  await t.click(e.switchElement('Suspended'));

  await t.expect(e.shareButton.hasAttribute('disabled')).notOk();

  await t.click(e.shareButton);
  await t.click(e.shareSwitch);
  await t.click(e.shareFilterCheckbox);

  const shareUrl = await e.shareUrl.value;

  await t.navigateTo(shareUrl);

  await t.expect(e.report.visible).ok();
  await t.expect(e.report.textContent).contains('No data to display');
});

test('remove a report from a dashboard', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');
  await u.save(t);
  await u.gotoOverview(t);
  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'New Report');

  await t.click(e.report);
  await t.click(e.reportDeleteButton);
  await u.save(t);

  await t.expect(e.report.exists).notOk();
});

test('external datasources', async (t) => {
  await u.createNewDashboard(t);

  await t.click(e.addButton);

  await t.takeElementScreenshot(e.reportModal, 'dashboard/dashboard-addAReportModal.png');

  await t.click(e.externalSourceLink);
  await t.typeText(e.externalSourceInput, 'http://example.com/');

  await t.click(e.addReportButton);

  await t.switchToIframe(e.externalReport);

  await t.expect(e.exampleHeading.textContent).contains('Example Domain');
});

test('deleting', async (t) => {
  await u.createNewDashboard(t);

  await u.save(t);

  await t.click(e.deleteButton);
  await t.click(e.modalConfirmbutton);

  await t.expect(e.dashboard.exists).notOk();
});

test('filters', async (t) => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt with alternative correlation variable', 'All');
  await u.selectView(t, 'Raw Data');
  await u.save(t);
  await u.gotoOverview(t);

  await u.createNewDashboard(t);
  await u.addReportToDashboard(t, 'New Report');

  await u.save(t);
  await t.click(e.editButton);

  await t.click(e.addFilterButton);
  await t.click(e.option('Instance State'));
  await t.click(e.addFilterButton);
  await t.click(e.option('Start Date'));
  await t.click(e.addFilterButton);
  await t.click(e.option('Variable'));

  await t.typeText(Filter.typeaheadInput, 'invoiceCategory', {replace: true});
  await t.click(Filter.typeaheadOption('invoiceCategory'));
  await t.click(Filter.multiSelectValue('Software License Costs'));
  await t.click(Filter.multiSelectValue('Travel Expenses'));
  await t.click(Filter.multiSelectValue('Misc'));

  await t.click(Filter.confirmButton);

  await t.resizeWindow(1200, 550);
  await t.takeElementScreenshot(e.dashboardContainer, 'dashboard/filter-editMode.png', {
    crop: {bottom: 250},
  });

  await u.save(t);

  await t.expect(e.report.visible).ok();

  await t.click(e.instanceStateFilter);
  await t.click(e.switchElement('Running'));

  await t.click(e.selectionFilter);
  await t.click(e.switchElement('Software License Costs'));
  await t.click(e.switchElement('Misc'));

  await t.takeElementScreenshot(e.dashboardContainer, 'dashboard/filter-viewMode.png', {
    crop: {bottom: 450},
  });
  await t.maximizeWindow();

  await t.expect(e.report.visible).ok();
});
