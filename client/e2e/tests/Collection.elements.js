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
export const collectionContextMenu = Selector('.Collection > .header .Dropdown button');
export const editCollectionNameButton = Selector(
  '.Collection .header .Dropdown .DropdownOption'
).withText('Edit');
export const copyCollectionButton = Selector(
  '.Collection .header .Dropdown .DropdownOption'
).withText('Copy');
export const deleteCollectionButton = Selector(
  '.Collection .header .Dropdown .DropdownOption'
).withText('Delete');
export const entityTab = Selector('.Collection .header .navigation a').withText(
  'Dashboards & Reports'
);
export const userTab = Selector('.Collection .header .navigation a').withText('Users');
export const alertTab = Selector('.Collection .header .navigation a').withText('Alerts');
export const sourcesTab = Selector('.Collection .header .navigation a').withText('Data Sources');
export const addButton = Selector('.Collection .content .header .Button');
export const optionsButton = Selector('.Typeahead .optionsButton');
export const typeaheadInput = Selector('.Typeahead input');
export const typeaheadOption = text => Selector('.Typeahead .DropdownOption').withText(text);
export const checkbox = text => Selector('.Checklist .label').withText(text);
export const confirmModalButton = Selector('.confirm.Button');
export const managerName = Selector('.ListItem.user')
  .withText('Manager')
  .find('.entityName');
export const userItem = text => Selector('.ListItem.user').withText(text);
export const groupItem = Selector('.ListItem.group');
export const processItem = Selector('.ListItem.process');
export const decisionItem = Selector('.ListItem.decision');
export const tenantSource = Selector('.ButtonGroup .Button').withText('Tenant');
export const roleOption = text => Selector('.Modal .LabeledInput .label.after').withText(text);
export const userList = Selector('.UserList');
export const sourcesList = Selector('.SourcesList');
export const addUserModal = Selector('.AddUserModal');
export const addSourceModal = Selector('.AddSourceModal');
export const logoutButton = Selector('.LogoutButton');
