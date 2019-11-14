import config from '../config';
import { login } from '../utils';

import * as Login from './Login.elements.js';
import * as Header from './Header.elements';

fixture('Login')
  .page(config.endpoint)

test('Log in with invalid user account', async t => {
  
  await t
    .expect(Login.passwordInput.getAttribute('type')).eql('password')
    .typeText(Login.usernameInput, 'demo')
    .typeText(Login.passwordInput, 'wrong-password')
    .click(Login.submitButton)
    .expect(Login.errorMessage.textContent).eql('Username and Password do not match');    
});

test('Log in with valid user account', async t => {

  await t
    .typeText(Login.usernameInput, 'demo')
    .typeText(Login.passwordInput, 'demo')
    .click(Login.submitButton)
    .expect(Header.brandLink.textContent).eql('Camunda Operate');  
});

test('Log out',async t => {
  await login(t);

  await t
    .click(Header.userDropdown)
    .click(Header.logoutItem)
    .expect(Login.submitButton.exists).ok();
});
