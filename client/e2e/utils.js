/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export async function login(t, user = 'demo') {
  await t
    .maximizeWindow()
    .typeText('input[name="username"]', user)
    .typeText('input[name="password"]', user)
    .click('.primary');
}

export async function createNewReport(t) {
  await t.click('.CreateNewButton');
  await t.click(Selector('.Submenu').withText('New Report'));
  await t.click(Selector('a').withText('Process Report'));
}

export async function selectDefinition(t, name, version = 'Specific version') {
  await t
    .click('.Popover.DefinitionSelection')
    .typeText('.Typeahead.name input', name, {replace: true})
    .click(Selector('.Typeahead.name .DropdownOption').withText(name))
    .click('.VersionPopover')
    .click(Selector('.label').withText(version))
    .click('.Popover.DefinitionSelection');
}

const selectControlPanelOption = type => async (t, name, subname) => {
  const dropdown = Selector('.label')
    .withText(type)
    .nextSibling();

  await t.click(dropdown.find('button')).click(dropdown.find('.DropdownOption').withText(name));

  if (subname) {
    await t.click(dropdown.find('.Submenu .DropdownOption').withText(subname));
  }
};

export const selectView = selectControlPanelOption('View');
export const selectGroupby = selectControlPanelOption('Group By');
export const selectVisualization = selectControlPanelOption('Visualization');

export async function save(t) {
  await t.click('.save-button');
  await t.expect(Selector('.edit-button').visible).ok();
}

export async function cancel(t) {
  await t.click('.cancel-button');
  await t.expect(Selector('.edit-button').visible).ok();
}

export async function selectAggregation(t, type) {
  await t.click('.Configuration .Popover');
  await t.click('.AggregationType .Select');
  await t.click(Selector('.AggregationType .DropdownOption').withText(type));
  await t.click('.Configuration .Popover');
}

export async function gotoOverview(t) {
  await t.click(Selector('a').withText('Home'));
}

export async function createNewDashboard(t) {
  await t.click('.CreateNewButton');
  await t.click(Selector('a').withText('New Dashboard'));
}

export async function addReportToDashboard(t, name) {
  await t
    .click('.AddButton')
    .click('.ReportModal .optionsButton')
    .click(Selector('.ReportModal .DropdownOption').withText(name))
    .click(Selector('.ReportModal button').withText('Add Report'));
}
