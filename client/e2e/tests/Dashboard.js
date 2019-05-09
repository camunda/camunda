/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as e from './Dashboard.elements.js';

fixture('Dashboard')
  .page(config.endpoint)
  .before(setup)
  .beforeEach(u.login);

test('create a report and add it to the Dashboard', async t => {
  await u.createNewReport(t);
  await u.selectDefinition(t, 'Invoice Receipt', '2');
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

test('renaming a dashboard', async t => {
  await t.hover(e.dashboard);
  await t.click(e.editButton);
  await t.typeText(e.nameEditField, 'New Name', {replace: true});

  await u.save(t);

  await t.expect(e.dashboardName.textContent).eql('New Name');
});

test('cancel changes', async t => {
  await t.hover(e.dashboard);
  await t.click(e.editButton);
  await t.typeText(e.nameEditField, 'Another new Name', {replace: true});

  await u.cancel(t);

  await t.expect(e.dashboardName.textContent).eql('New Name');
});

// we cannot automatically test this as browsers do not like going fullscreen automatically
// test('view in fullscreen and dark mode', async t => {
//   await t.click(e.dashboard);
//   await t.click(e.fullscreenButton);

//   await t.expect(e.header.exists).notOk();
//   await t.expect(e.themeButton.visible).ok();

//   await t.click(e.themeButton);

//   await t.expect(e.fullscreenContent.getStyleProperty('background-color')).eql('#222');
// });

test('sharing', async t => {
  await t.click(e.dashboard);
  await t.click(e.shareButton);
  await t.click(e.shareSwitch);

  const shareUrl = await e.shareUrl.value;

  await t.navigateTo(shareUrl);

  await t.expect(e.report.visible).ok();
  await t.expect(e.report.textContent).contains('invoice');
  await t.expect(e.report.textContent).contains('Start Date');
});

test('remove a report from a dashboard', async t => {
  await t.hover(e.dashboard);
  await t.click(e.editButton);
  await t.click(e.reportDeleteButton);
  await u.save(t);

  await t.expect(e.report.exists).notOk();
});

test('external datasources', async t => {
  await t.hover(e.dashboard);
  await t.click(e.editButton);

  await t.click(e.addButton);
  await t.click(e.externalSourceLink);
  await t.typeText(e.externalSourceInput, config.endpoint + '/license.html');

  await t.click(e.addReportButton);

  await t.switchToIframe(e.externalReport);

  await t.expect(e.formLabel.textContent).contains('License Key');
});

test('deleting', async t => {
  await t.click(e.dashboard);

  await t.click(e.deleteButton);
  await t.click(e.modalConfirmbutton);

  await t.expect(e.dashboard.exists).notOk();
});
