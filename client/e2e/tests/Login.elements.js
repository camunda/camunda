/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const usernameInput = Selector('[name="username"]');
export const passwordInput = Selector('[name="password"]');
export const submitButton = Selector('[data-test="login-button"]');
export const errorMessage = Selector('form > div');
