/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const editButton = Selector('[data-test="enter-edit-btn"]');
export const addButton = Selector('[data-test="enter-add-btn"]');
export const editText = Selector('[data-test="edit-value"]');
export const addKey = Selector('[data-test="add-key"]');
export const addValue = Selector('[data-test="add-value"]');
export const saveVariable = Selector('[data-test="save-var-inline-btn"]');
export const editVariableSpinner = Selector(
  '[data-test="edit-variable-spinner"]'
);
export const operationSpinner = Selector('[data-test="operation-spinner"]');
export const cancelEdit = Selector('[data-test="exit-edit-inline-btn"]');
