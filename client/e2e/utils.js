/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

import config from './config';
import * as Homepage from './tests/Homepage.elements.js';

let instanceCount = {
  Chrome: 0,
  headless: 0,
  Firefox: 0,
  'Microsoft Edge': 0,
};

export async function login(t, userHandle = 'user1') {
  const user = getUser(t, userHandle);

  t.ctx.users = t.ctx.users || [];
  if (!t.ctx.users.find((existingUser) => existingUser.username === user.username)) {
    t.ctx.users.push(user);
  }

  await t
    .maximizeWindow()
    .typeText('input[name="username"]', user.username)
    .typeText('input[name="password"]', user.password)
    .click('.primary');
}

export function getUser(t, userHandle) {
  const {browserConnection} = t.testRun;
  const name = t.browser.headless ? 'headless' : t.browser.name;

  if (typeof browserConnection.userId === 'undefined') {
    browserConnection.userId = instanceCount[name]++;
  }

  return config.users[name][browserConnection.userId][userHandle];
}

export async function createNewReport(t) {
  await t.click('.CreateNewButton');
  await t.click(Selector('.Submenu').withText('New Report'));
  await t.click(Selector('.Submenu .DropdownOption').withText('Process Report'));
  await t.click(Selector('.Button').withText('Blank report'));
  await t.click(Selector('.Modal .primary.confirm.Button'));
}

export async function selectReportDefinition(t, name, version = 'Specific version') {
  await t
    .click('.AddDefinition')
    .click(Selector('.Checklist .label').withText(name))
    .click('.Modal .primary.Button');

  await t.click(Selector('.DefinitionList li').withText(name).find('.Popover__button'));
  await t.click('.VersionPopover');
  await t.click(Selector('.label').withText('Specific versions'));
  await t.click(Selector('.specificVersions input[type="checkbox"]').nth(0));

  if (typeof version === 'string') {
    await t.click(Selector('.label').withText(version));
  } else {
    await t.click(Selector('.label').withText('Specific versions'));
    await t.click(Selector('.specificVersions input[type="checkbox"]').nth(0));
    for (let i = 0; i < version.length; i++) {
      await t.click(Selector('.specificVersions input[type="checkbox"]').nth(-version[i]));
    }
  }

  await t.click(Selector('.DefinitionList li').withText(name).find('.Popover__button'));
}

export async function selectDefinition(t, name, version = 'Specific version') {
  await t
    .click('.Popover.DefinitionSelection')
    .typeText('.Typeahead input', name, {replace: true})
    .click(Selector('.Typeahead .DropdownOption').withText(name));

  await t.expect(Selector('.VersionPopover button').hasAttribute('disabled')).notOk();

  await t.click('.VersionPopover');

  if (typeof version === 'string') {
    await t.click(Selector('.label').withText(version));
  } else {
    await t.click(Selector('.label').withText('Specific versions'));
    await t.click(Selector('.specificVersions input[type="checkbox"]').nth(0));
    for (let i = 0; i < version.length; i++) {
      await t.click(Selector('.specificVersions input[type="checkbox"]').nth(-version[i]));
    }
  }

  await t.click('.Popover.DefinitionSelection');
}

const selectControlPanelOption = (type) => async (t, name, subname) => {
  if (type === 'View' && name !== 'Variable' && subname) {
    await selectView(t, name);
    await selectControlPanelOption('Measure')(t, subname);
  } else {
    const dropdown = Selector('.label').withText(type).nextSibling();

    await t.click(dropdown.find('button')).click(dropdown.find('.DropdownOption').withText(name));

    if (subname) {
      await t.click(dropdown.find('.Submenu .DropdownOption').withText(subname));
    }
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

export async function gotoOverview(t) {
  await t.click(Selector('a').withText('Home'));
}

export async function createNewDashboard(t) {
  await t.click('.CreateNewButton');
  await t.click(Selector('.DropdownOption').withText('New Dashboard'));
  await t.click(Selector('.Button').withText('Blank Dashboard'));
  await t.click(Homepage.modalConfirmbutton);
}

export async function addReportToDashboard(t, name) {
  await t
    .click('.AddButton')
    .click('.ReportModal .optionsButton')
    .click(Selector('.ReportModal .DropdownOption').withText(name))
    .click(Selector('.ReportModal button').withText('Add Report'))
    .click('.DashboardRenderer');
}
