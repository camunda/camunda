/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';
import setup from '../setup';
import config from '../config';

fixture(`Example Test`)
  .page(config.endpoint)
  .beforeEach(setup);

test('Create a report and put it on a dashboard', async t => {
  await login(t);
  await createNewReport(t);
  await selectDefinition(t, 'Invoice Receipt', '2');
  await selectView(t, 'Raw Data');
  await save(t);
  await gotoOverview(t);
  await createNewDashboard(t);
  await addReportToDashboard(t, 'New Report');
  await save(t);

  const Report = Selector('.ReportRenderer');

  await t.expect(Report.visible).ok();
  await t.expect(Report.textContent).contains('invoice');
  await t.expect(Report.textContent).contains('Start Date');
});

async function login(t) {
  await t
    .typeText('input[name="username"]', 'demo')
    .typeText('input[name="password"]', 'demo')
    .click('.Button--primary');
}

async function createNewReport(t) {
  await t.click(Selector('button').withText('Create Process Report'));
}

async function selectDefinition(t, name, version) {
  await t
    .click(Selector('button').withText('Select Process'))
    .click('.Typeahead.name input')
    .click(Selector('.Typeahead.name .DropdownOption').withText(name))
    .click('.version.Dropdown')
    .click(Selector('.version.Dropdown .DropdownOption').withText(version))
    .click('.processDefinitionPopover');
}

async function selectView(t, name) {
  const dropdown = Selector('.label')
    .withText('View')
    .nextSibling();

  await t.click(dropdown.find('button')).click(dropdown.find('.DropdownOption').withText(name));
}

async function save(t) {
  await t.click('.save-button');
}

async function gotoOverview(t) {
  await t.click(Selector('a').withText('Dashboards & Reports'));
}

async function createNewDashboard(t) {
  await t.click(Selector('button').withText('Create Dashboard'));
}

async function addReportToDashboard(t, name) {
  await t
    .click('.AddButton')
    .click('.ReportModal .optionsButton')
    .click(Selector('.ReportModal .DropdownOption').withText(name))
    .click(Selector('.ReportModal button').withText('Add Report'));
}
