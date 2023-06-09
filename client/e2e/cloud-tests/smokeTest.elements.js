/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const emptyTemplate = Selector('.templateContainer .name').withText('Blank report');
export const whatsNewCloseBtn = Selector('.WhatsNewModal button.close');
export const usernameInput = Selector('input[name="username"]');
export const passwordInput = Selector('input[name="password"]');
export const submitButton = Selector(':not([aria-hidden]) > button[type="submit"]');
