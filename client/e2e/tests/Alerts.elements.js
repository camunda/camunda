/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const list = Selector('.AlertList .content');
export const listItem = Selector('.ListItem');
export const newAlertButton = Selector('.AlertList .header .Button');
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
export const modal = Selector('.Modal__content-container');
