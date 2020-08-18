/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ClientFunction} from 'testcafe';

import {config} from '../config';
import {screen} from '@testing-library/testcafe';

const getPathname = ClientFunction(() => window.location.pathname);
const reloadPage = ClientFunction(() => {
  window.location.reload();
});

fixture('Login').page(config.endpoint);

test('redirect to the main page on login', async (t) => {
  await t
    .expect(screen.getByPlaceholderText('Password').getAttribute('type'))
    .eql('password');

  await t
    .typeText(screen.getByPlaceholderText('Username'), 'demo')
    .typeText(screen.getByPlaceholderText('Password'), 'demo')
    .click(screen.getByRole('button', {name: 'Login'}));
  await t.expect(await getPathname()).eql('/');
});

test('persistency of a session', async (t) => {
  await t
    .typeText(screen.getByPlaceholderText('Username'), 'demo')
    .typeText(screen.getByPlaceholderText('Password'), 'demo')
    .click(screen.getByRole('button', {name: 'Login'}));

  await reloadPage();

  await t.expect(await getPathname()).eql('/');
});

test('logout redirect', async (t) => {
  await t
    .typeText(screen.getByPlaceholderText('Username'), 'demo')
    .typeText(screen.getByPlaceholderText('Password'), 'demo')
    .click(screen.getByRole('button', {name: 'Login'}));

  await t.click(screen.getByRole('button', {name: 'Demo User'}));
  await t.click(screen.getByRole('button', {name: 'Logout'}));

  await t.expect(await getPathname()).eql('/login');
});

test('block form submission with empty fields', async (t) => {
  await t.click(screen.getByRole('button', {name: 'Login'}));
  await t.expect(await getPathname()).eql('/login');

  await t
    .typeText(screen.getByPlaceholderText('Username'), 'demo')
    .click(screen.getByRole('button', {name: 'Login'}));
  await t.expect(await getPathname()).eql('/login');

  await t
    .selectText(screen.getByPlaceholderText('Username'))
    .pressKey('delete')
    .typeText(screen.getByPlaceholderText('Password'), 'demo')
    .click(screen.getByRole('button', {name: 'Login'}));
  await t.expect(await getPathname()).eql('/login');
});

test('show error message on login failure', async (t) => {
  await t
    .typeText(screen.getByPlaceholderText('Username'), 'demo')
    .typeText(screen.getByPlaceholderText('Password'), 'wrong-password')
    .click(screen.getByRole('button', {name: 'Login'}));

  await t
    .expect(screen.getByText('Username and Password do not match.').exists)
    .ok();
});
