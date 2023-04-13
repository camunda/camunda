/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const list = Selector('.AlertList .content');
export const newAlertButton = Selector('.AlertList .header .Button.primary');
export const inputWithLabel = (label) => Selector('.Modal .label').withText(label).nextSibling();
export const editButton = Selector('[title="Edit Alert"]');
export const cancelButton = Selector('.Modal__actions button');
export const deleteButton = Selector('[title="Delete Alert"]');
export const webhookDropdown = Selector('.typeaheadInput').nth(1);
