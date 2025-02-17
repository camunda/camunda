/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Selector} from 'testcafe';
import {loadUsers} from './users.js';
import * as Homepage from './sm-tests/Homepage.elements.js';
import * as Common from './sm-tests/Common.elements.js';
import * as Report from './sm-tests/ProcessReport.elements.js';

let instanceCount = {
  Chrome: 0,
  headless: 0,
  Firefox: 0,
  'Microsoft Edge': 0,
};

let users = {};

export async function login(t, userHandle = 'user1') {
  users = await loadUsers();

  const user = getUser(t, userHandle);

  t.ctx.users = t.ctx.users || [];
  if (!t.ctx.users.find((existingUser) => existingUser.username === user.username)) {
    t.ctx.users.push(user);
  }

  await t
    .maximizeWindow()
    .typeText('input[name="username"]', user.username)
    .typeText('input[name="password"]', user.username)
    .click('[type="submit"]')
    .wait(1000);
}

export function getUser(t, userHandle) {
  const {browserConnection} = t.testRun;
  const name = t.browser.headless ? 'headless' : t.browser.name;

  if (typeof browserConnection.userId === 'undefined') {
    browserConnection.userId = instanceCount[name]++;
  }

  return users[name][browserConnection.userId][userHandle];
}

export async function createNewReport(t) {
  await t.click(Common.createNewButton);
  await t.click(Common.menuOption('Report'));
  await t.click(Selector('button').withText('Blank report'));
  await t.click(Selector(Common.modalConfirmButton));
  await toggleReportAutoPreviewUpdate(t);
}

export async function selectVersion(t, selector, version) {
  await t.click(selector.find('.Popover button'));
  await t.click('.VersionPopover');

  if (typeof version === 'string') {
    await t.click(Common.radioButton(version));
  } else {
    await t.click(Common.radioButton('Specific versions'));
    for (let i = 0; i < version.length; i++) {
      await t.click(Common.checkbox(`${version[i]}`));
    }
  }

  await t.click(selector.find('.Popover button'));
}

export async function selectReportDefinition(t, name, version) {
  await t
    .click('.AddDefinition')
    .click(Selector('.Checklist tr').withText(name))
    .click(Common.modalConfirmButton);

  if (version) {
    selectVersion(t, Selector('.DefinitionList li').withText(name), version);
  }
}

export async function selectDefinition(t, name, version = 'Specific version') {
  await t
    .click('.Popover.DefinitionSelection')
    .typeText('.popoverContent input[type="text"]', name, {replace: true})
    .click(Common.carbonOption(name));

  await t.expect(Selector('.VersionPopover button').hasAttribute('disabled')).notOk();

  await t.click('.VersionPopover');

  if (typeof version === 'string') {
    await t.click(Common.radioButton(version));
  } else {
    await t.click(Common.radioButton('Specific versions'));
    await t.click(Common.checkbox(`${version[0]}`));
    for (let i = 0; i < version.length; i++) {
      await t.click(Common.checkbox(`${version[i]}`));
    }
  }

  await t.click('.Popover.DefinitionSelection');
}

const selectControlPanelOption = (type) => async (t, name, subname) => {
  if (type === 'View' && name !== 'Variable' && subname) {
    await selectView(t, name);
    await selectControlPanelOption('Measure')(t, subname);
  } else {
    const dropdownButton =
      type === 'Visualization'
        ? Report.visualizationDropdown
        : Selector('.label').withText(type).nextSibling().find('button');
    const selectedOption = await dropdownButton.innerText;
    if (selectedOption === name) {
      return;
    }

    await t.click(dropdownButton).click(Common.menuOption(name));

    if (subname) {
      await t.hover(Common.menuOption(name));
      await t.click(Common.submenuOption(subname));
    }
  }
};

export const selectView = selectControlPanelOption('View');
export const selectGroupby = selectControlPanelOption('Group by');
export const selectVisualization = selectControlPanelOption('Visualization');

export async function save(t) {
  await t.click('.save-button');
  await t.expect(Common.editButton.visible).ok();
}

export async function cancel(t) {
  await t.click('.cancel-button');
  await t.expect(Common.editButton.visible).ok();
}

export async function gotoOverview(t) {
  await t.click(Selector('a').withText('Collections'));
}

export async function createNewDashboard(t) {
  await t.click(Common.createNewButton);
  await t.click(Common.menuOption('Dashboard'));
  await t.click(Homepage.blankDashboardButton);
  await t.click(Common.modalConfirmButton);
}

export async function addReportToDashboard(t, name) {
  await t
    .click('.AddButton')
    .click('.CreateTileModal #addReportSelector')
    .click(Common.carbonOption(name))
    .click(Selector('.CreateTileModal button').withText('Add tile'))
    .click('.DashboardRenderer');
}

export async function bulkDeleteAllItems(t) {
  await t.click(Common.collectionsPage);
  await t.click(Common.selectAllCheckbox);
  await t.click(Common.bulkDelete);
  await t.click(Common.modalConfirmButton);
}

export async function toggleReportAutoPreviewUpdate(t) {
  const isToggleOn = await Selector('.updatePreview button')['aria-checked'];
  if (!isToggleOn) {
    await t.click('.updatePreview');
  }
}

export async function addEditEntityDescription(t, description, screenshotPath) {
  await t.click(Common.addDescriptionButton);

  if (!description) {
    await t.selectText(Common.descriptionModalInput).pressKey('delete');
  } else {
    await t.typeText(Common.descriptionModalInput, description, {replace: true});
  }

  if (screenshotPath) {
    await t.takeElementScreenshot(Common.descriptionModal, screenshotPath);
  }

  await t.click(Common.confirmButton);
}
