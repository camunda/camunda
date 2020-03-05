/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as Login from './tests/Login.elements.js';

export async function login(t, user = 'demo') {
  await t
    .maximizeWindow()
    .typeText(Login.usernameInput, user)
    .typeText(Login.passwordInput, user)
    .click(Login.submitButton);
}
