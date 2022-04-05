/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ClientFunction} from 'testcafe';

import {config} from '../config';
import {screen} from '@testing-library/testcafe';

const getPathname = ClientFunction(() => window.location.pathname);
const getURL = ClientFunction(
  () => `${window.location.pathname}${window.location.search}`,
);
const reloadPage = ClientFunction(() => {
  window.location.reload();
});

fixture('Login').page(config.endpoint);

test('redirect to the main page on login', async (t) => {
  await t
    .expect(screen.getByLabelText('Password').getAttribute('type'))
    .eql('password');

  await t
    .typeText(screen.getByLabelText('Username'), 'demo')
    .typeText(screen.getByLabelText('Password'), 'demo')
    .click(screen.getByRole('button', {name: 'Login'}));
  await t.expect(await getPathname()).eql('/');
});

test('persistency of a session', async (t) => {
  await t
    .typeText(screen.getByLabelText('Username'), 'demo')
    .typeText(screen.getByLabelText('Password'), 'demo')
    .click(screen.getByRole('button', {name: 'Login'}));

  await reloadPage();

  await t.expect(await getPathname()).eql('/');
});

test('logout redirect', async (t) => {
  await t
    .typeText(screen.getByLabelText('Username'), 'demo')
    .typeText(screen.getByLabelText('Password'), 'demo')
    .click(screen.getByRole('button', {name: 'Login'}));

  await t.click(screen.getByRole('button', {name: 'demo'}));
  await t.click(screen.getByRole('button', {name: 'Logout'}));

  await t.expect(await getPathname()).eql('/login');
});

test('block form submission with empty fields', async (t) => {
  await t.click(screen.getByRole('button', {name: 'Login'}));
  await t.expect(await getPathname()).eql('/login');

  await t
    .typeText(screen.getByLabelText('Username'), 'demo')
    .click(screen.getByRole('button', {name: 'Login'}));
  await t.expect(await getPathname()).eql('/login');

  await t
    .selectText(screen.getByLabelText('Username'))
    .pressKey('delete')
    .typeText(screen.getByLabelText('Password'), 'demo')
    .click(screen.getByRole('button', {name: 'Login'}));
  await t.expect(await getPathname()).eql('/login');
});

test('show error message on login failure', async (t) => {
  await t
    .typeText(screen.getByLabelText('Username'), 'demo')
    .typeText(screen.getByLabelText('Password'), 'wrong-password')
    .click(screen.getByRole('button', {name: 'Login'}));

  await t
    .expect(screen.getByText('Username and Password do not match').exists)
    .ok();
});

test('redirect to the correct URL after login', async (t) => {
  const selectedTaskURL = '/123';
  const selectedFilterUrl = '/?filter=unclaimed';
  const selectedTaskAndFilterURL = '/123?filter=unclaimed';

  await t
    .navigateTo(selectedTaskURL)
    .typeText(screen.getByLabelText('Username'), 'demo')
    .typeText(screen.getByLabelText('Password'), 'demo')
    .click(screen.getByRole('button', {name: 'Login'}))
    .expect(getURL())
    .eql(selectedTaskURL)
    .click(screen.getByRole('button', {name: 'demo'}))
    .click(screen.getByRole('button', {name: /logout/i}));

  await t
    .navigateTo(selectedFilterUrl)
    .typeText(screen.getByLabelText('Username'), 'demo')
    .typeText(screen.getByLabelText('Password'), 'demo')
    .click(screen.getByRole('button', {name: 'Login'}))
    .expect(getURL())
    .eql(selectedFilterUrl)
    .click(screen.getByRole('button', {name: 'demo'}))
    .click(screen.getByRole('button', {name: /logout/i}));

  await t
    .navigateTo(selectedTaskAndFilterURL)
    .typeText(screen.getByLabelText('Username'), 'demo')
    .typeText(screen.getByLabelText('Password'), 'demo')
    .click(screen.getByRole('button', {name: 'Login'}))
    .expect(getURL())
    .eql(selectedTaskAndFilterURL)
    .click(screen.getByRole('button', {name: 'demo'}))
    .click(screen.getByRole('button', {name: /logout/i}));
});
