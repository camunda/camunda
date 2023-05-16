/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const collectionTitle = Selector('.Collection .header .text');
export const collectionBreadcrumb = Selector('.cds--header__menu-bar .breadcrumb');
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
export const remove = (element) => element.find('.DropdownOption').withText('Remove');
export const entitiesTab = Selector('.Collection .header .navigation a').withText('Dashboards');
export const userTab = Selector('.Collection .header .navigation a').withText('Users');
export const alertTab = Selector('.Collection .header .navigation a').withText('Alerts');
export const sourcesTab = Selector('.Collection .header .navigation a').withText('Data Sources');
export const addButton = Selector('.Collection .content .header .Button.primary');
export const typeaheadInput = Selector('.Typeahead input');
export const checkbox = (text) => Selector('.Checklist .label').withText(text);
export const managerName = Selector('.ListItem').withText('Manager').find('.name .entity');
export const userItem = (text) => Selector('.ListItem').withText('User').withText(text);
export const groupItem = Selector('.ListItem').withText('User Group');
export const processItem = Selector('.ListItem').withText('Process');
export const decisionItem = Selector('.ListItem').withText('Decision');
export const roleOption = (text) =>
  Selector('.CarbonModal.is-visible .LabeledInput .label.after').withText(text);
export const userList = Selector('.UserList');
export const addUserModal = Selector('.AddUserModal');
export const addSourceModal = Selector('.SourcesModal');
export const logoutButton = Selector('header button').withText('Logout');
export const usernameDropdown = Selector('header button').withAttribute('aria-label', 'Open User');
export const usersTypeahead = Selector('.MultiUserInput .Input');
export const searchField = Selector('.SearchInput input');
export const selectAllCheckbox = Selector('.Table thead .cds--table-column-checkbox label');
export const itemCheckbox = (idx) =>
  Selector('.Table tbody tr').nth(idx).find('.cds--table-column-checkbox label');
