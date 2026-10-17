/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type Browser} from '@playwright/test';

import {env} from '../env';
import {USERS, type UserName} from '../seed/dataset';
import {storageStatePath} from './storageState';

// Contexts created inside tests inherit the test's session unless told otherwise.
export const NO_SESSION = {cookies: [], origins: []};

// The first login of a run waits for the dev server to compile the app on demand.
export const LOGIN_TIMEOUT = 60_000;

// Logs in through Keycloak and stores the session. Returns the storage state path.
export async function login(browser: Browser, user: UserName, id: string): Promise<string> {
  const {username, password} = USERS[user];
  const path = storageStatePath(`${user}-${id}`);
  const context = await browser.newContext({baseURL: env.optimizeUrl, storageState: NO_SESSION});
  const page = await context.newPage();
  page.setDefaultTimeout(LOGIN_TIMEOUT);

  await page.goto('/');
  await page.getByLabel('Username or email').fill(username);
  await page.getByLabel('Password', {exact: true}).fill(password);
  await page.getByRole('button', {name: 'Log in'}).click();
  await expect(page.getByRole('navigation', {name: 'Main navigation'})).toBeVisible({
    timeout: LOGIN_TIMEOUT,
  });

  await context.storageState({path});
  await context.close();
  return path;
}
