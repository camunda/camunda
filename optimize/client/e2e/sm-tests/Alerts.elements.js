/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Selector} from 'testcafe';

import {listItem} from './Common.elements';

export const list = Selector('.AlertList');
export const newAlertButton = Selector('.AlertList button.createAlert');
export const inputWithLabel = (label) =>
  Selector('.AlertModal label').withText(label).parent().find('input');
export const copyNameInput = Selector('.Modal.is-visible input');
export const editButton = Selector('[title="Edit Alert"]');
export const cancelButton = Selector('.Modal.is-visible .cds--modal-footer .cds--btn:nth-child(1)');
export const deleteButton = Selector('[title="Delete Alert"]');
export const alertListItem = listItem('alert');
