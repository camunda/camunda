import {Selector} from 'testcafe';

export const usernameInput = Selector('[name="username"]');
export const passwordInput = Selector('[name="password"]');
export const submitButton = Selector('[data-test="login-button"]');
export const errorMessage = Selector('form > div');
