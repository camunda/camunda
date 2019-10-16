/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const navItem = Selector('header a').withText('Alerts');
export const list = Selector('.entityList');
export const newAlertButton = Selector('.createButton');
export const primaryModalButton = Selector('.Modal .Modal__actions .primary');
export const inputWithLabel = label =>
  Selector('.Modal .label')
    .withText(label)
    .nextSibling();
export const reportTypeahead = Selector('.Modal .Typeahead');
export const reportTypeaheadOption = text =>
  Selector('.Modal .Typeahead .DropdownOption').withText(text);
export const editButton = Selector('[title="Edit Alert"]');
export const cancelButton = Selector('.Modal__actions button');
export const deleteButton = Selector('[title="Delete Alert"]');
