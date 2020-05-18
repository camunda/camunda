/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

const loginButton = Selector('button').withText('Login');
const logoutButton = Selector('button').withText('Logout');

export {loginButton, logoutButton};
