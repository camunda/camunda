/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Selector, ClientFunction} from 'testcafe';
import config from './config';
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

// Submit the Keycloak login form from inside the page via a same-origin fetch of the form's
// own `action`, rather than a synthetic button click. Returns 'ok' on success or a short error
// string. See `login()` for why this is done programmatically instead of through the UI.
const submitLoginForm = ClientFunction((username, password) => {
  const form =
    document.getElementById('kc-form-login') ||
    (document.querySelector('input[name="username"]') || {}).form ||
    document.querySelector('form');
  if (!form) {
    return Promise.resolve('no-form');
  }

  // Build the POST body from the form's existing fields (preserving Keycloak hidden inputs such
  // as `credentialId`) and overlay the credentials.
  const params = new URLSearchParams();
  new FormData(form).forEach((value, key) => params.set(key, value));
  params.set('username', username);
  params.set('password', password);

  // `redirect: 'manual'` so we never try to read the cross-origin Optimize callback response
  // (which CORS would block): we only need Keycloak's 302, whose Set-Cookie establishes the SSO
  // session on the Keycloak origin. A successful authentication answers with that 302 (surfaced as
  // an opaque redirect); a rejected one re-renders the login form as a readable 200, so only an
  // opaque redirect counts as success.
  return fetch(form.getAttribute('action'), {
    method: 'POST',
    body: params,
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    redirect: 'manual',
    credentials: 'include',
  })
    .then((res) => (res.type === 'opaqueredirect' ? 'ok' : 'rejected-http-' + res.status))
    .catch((e) => 'fetch-error:' + e);
});

export async function login(t, userHandle = 'user1') {
  users = await loadUsers();

  const user = getUser(t, userHandle);

  t.ctx.users = t.ctx.users || [];
  if (!t.ctx.users.find((existingUser) => existingUser.username === user.username)) {
    t.ctx.users.push(user);
  }

  await t.maximizeWindow();

  // Start every login from a clean cookie jar so a lingering Keycloak SSO session from a previous
  // test (possibly a different user) cannot silently authenticate us as the wrong identity.
  await t.deleteCookies();
  await t.navigateTo(config.endpoint);

  const usernameInput = Selector('input[name="username"]');

  // The OIDC redirect chain (Optimize -> Keycloak) means the login form is briefly absent right
  // after navigation, so wait for it. If a Keycloak SSO cookie survived the deleteCookies above
  // (cross-origin clearing is best-effort), Optimize logs us straight in and no form renders — in
  // that case we are already authenticated, so return.
  if (!(await usernameInput.with({timeout: 20000}).visible)) {
    return;
  }

  // We log in by POSTing the Keycloak form programmatically instead of clicking the submit button.
  //
  // Root cause this avoids: under TestCafe native automation, the synthetic submit (button click /
  // Enter) is intermittently not delivered to Chrome in the CI environment, so the credentials are
  // typed but the form never POSTs and the browser is stranded on the login page (manifesting as
  // "selector did not match" on the app shell). The Keycloak server itself authenticates reliably
  // every time (verified directly over HTTP), so we submit the form via a same-origin in-page fetch
  // of its `action`, which is deterministic and not subject to the input-dispatch race. Keycloak's
  // response sets the SSO session cookie; navigating to Optimize then completes the OIDC login via
  // that session (no second form is shown).
  //
  // In the self-managed e2e Keycloak realm the test users use password == username.
  const result = await submitLoginForm(user.username, user.username);
  if (result !== 'ok') {
    throw new Error(`Programmatic Keycloak login failed for ${user.username}: ${result}`);
  }

  // Complete the OIDC flow against the freshly established SSO session and confirm we land in the
  // authenticated app (the login form is gone).
  await t.navigateTo(config.endpoint);
  await t.expect(usernameInput.exists).notOk({timeout: 20000});
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
