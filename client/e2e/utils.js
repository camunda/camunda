import {Selector} from 'testcafe';

import * as Login from './tests/Login.elements.js';

export async function login(t, user = 'demo') {
  await t
    .maximizeWindow()
    .typeText(Login.usernameInput, user)
    .typeText(Login.passwordInput, user)
    .click(Login.submitButton);
}
