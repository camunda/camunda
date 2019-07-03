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
    .click('.primary');
}

export async function createNewReport(t) {
  await t.click(Selector('button').withText('Create Process Report'));
}

export async function selectDefinition(t, name, version) {
  await t
    .click('.Popover.DefinitionSelection')
    .typeText('.Typeahead.name input', name, {replace: true})
    .click(Selector('.Typeahead.name .DropdownOption').withText(name))
    .click('.version.Dropdown')
    .click(Selector('.version.Dropdown .DropdownOption').withText(version))
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
}

export async function cancel(t) {
  await t.click('.cancel-button');
}

export async function selectAggregation(t, type) {
  await t.click('.Configuration .Popover');
  await t.click('.AggregationType .Select');
  await t.click(Selector('.AggregationType .DropdownOption').withText(type));
  await t.click('.Configuration .Popover');
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
