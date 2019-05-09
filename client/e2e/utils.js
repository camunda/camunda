/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export async function login(t) {
  await t
    .typeText('input[name="username"]', 'demo')
    .typeText('input[name="password"]', 'demo')
    .click('.Button--primary');
}

export async function createNewReport(t) {
  await t.click(Selector('button').withText('Create Process Report'));
}

export async function selectDefinition(t, name, version) {
  await t
    .click(Selector('button').withText('Select Process'))
    .click('.Typeahead.name input')
    .click(Selector('.Typeahead.name .DropdownOption').withText(name))
    .click('.version.Dropdown')
    .click(Selector('.version.Dropdown .DropdownOption').withText(version))
    .click('.processDefinitionPopover');
}

export async function selectView(t, name) {
  const dropdown = Selector('.label')
    .withText('View')
    .nextSibling();

  await t.click(dropdown.find('button')).click(dropdown.find('.DropdownOption').withText(name));
}

export async function save(t) {
  await t.click('.save-button');
}

export async function cancel(t) {
  await t.click('.cancel-button');
}

export async function gotoOverview(t) {
  await t.click(Selector('a').withText('Dashboards & Reports'));
}

export async function createNewDashboard(t) {
  await t.click(Selector('button').withText('Create Dashboard'));
}

export async function addReportToDashboard(t, name) {
  await t
    .click('.AddButton')
    .click('.ReportModal .optionsButton')
    .click(Selector('.ReportModal .DropdownOption').withText(name))
    .click(Selector('.ReportModal button').withText('Add Report'));
}
