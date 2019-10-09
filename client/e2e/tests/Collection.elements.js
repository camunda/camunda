/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const collectionTitle = Selector('.Collection .header .name');
export const createNewMenu = Selector('.CreateNewButton');
export const option = text => Selector('.DropdownOption').withText(text);
export const collectionBreadcrumb = Selector('.HeaderNav .breadcrumb');
export const dashboardItem = Selector('.ListItem.dashboard');
export const collectionContextMenu = Selector('.Collection .header .Dropdown button');
export const editCollectionNameButton = Selector(
  '.Collection .header .Dropdown .DropdownOption'
).withText('Edit');
export const deleteCollectionButton = Selector(
  '.Collection .header .Dropdown .DropdownOption'
).withText('Delete');
export const entityTab = Selector('.Collection .header .navigation a').withText(
  'Dashboards & Reports'
);
export const userTab = Selector('.Collection .header .navigation a').withText('Users');
export const addUserButton = Selector('.Collection .content .header .Button');
export const groupNameInput = Selector('.groupIdInput input');
export const confirmModalButton = Selector('.confirm.Button');
export const userItem = Selector('.ListItem.user');
export const groupItem = Selector('.ListItem.group');
export const managerOption = Selector('.Modal .LabeledInput .label.after').withText('Manager');
export const viewerOption = Selector('.Modal .LabeledInput .label.after').withText('Viewer');
