/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ClientFunction} from 'testcafe';

import {config} from '../config';
import {loginButton, logoutButton} from './Login.selectors';

const getPathname = ClientFunction(() => window.location.pathname);
const reloadPage = ClientFunction(() => {
  window.location.reload();
});

fixture('Login').page(config.endpoint);

test('redirect to the main page on login', async (t) => {
  await t.click(loginButton);

  await t.expect(await getPathname()).eql('/');
});

test('persistency of a session', async (t) => {
  await t.click(loginButton);

  await reloadPage();

  await t.expect(await getPathname()).eql('/');
});

test('logout redirect', async (t) => {
  await t.click(loginButton);

  await t.click(logoutButton);

  await t.expect(await getPathname()).eql('/login');
});
