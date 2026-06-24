/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Selector} from 'testcafe';

export const emptyTemplate = Selector('.templateContainer .name').withText('Blank report');
export const usernameInput = Selector('input[name="username"]');
export const passwordInput = Selector('input[name="password"]');
export const submitButton = Selector(':not([aria-hidden]) > button[type="submit"]');
