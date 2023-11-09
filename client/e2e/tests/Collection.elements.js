/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

import {overflowMenuOption} from './Common.elements';

export const navItem = Selector('.NavItem a').withText('Collections');
export const collectionTitle = Selector('.Collection .header .text');
export const collectionBreadcrumb = Selector('.cds--header__menu-bar .breadcrumb');
export const collectionContextMenu = Selector(
  '.Collection > .header .cds--overflow-menu__wrapper button'
);
export const editCollectionNameButton = overflowMenuOption('Edit');
export const copyCollectionButton = overflowMenuOption('Copy');
export const deleteCollectionButton = overflowMenuOption('Delete');
export const remove = (element) => element.find('.DropdownOption').withText('Remove');
const tabButton = Selector('.Collection .content .cds--tabs__nav-item');
export const entityTab = tabButton.withText('Dashboards & Reports');
export const entitiesTab = tabButton.withText('Dashboards');
export const userTab = tabButton.withText('Users');
export const alertTab = tabButton.withText('Alerts');
export const sourcesTab = tabButton.withText('Data Sources');
export const activeTab = Selector('.Collection .content .cds--tab-content:not([hidden]');
export const addButton = activeTab.find('.cds--btn--primary');
export const typeaheadInput = Selector('.Typeahead input');
export const checkbox = (text) => Selector('.Checklist tr').withText(text);
export const managerName = Selector('.ListItem').withText('Manager').find('.name .entity');
export const userItem = (text) => Selector('.ListItem').withText('User').withText(text);
export const groupItem = Selector('.ListItem').withText('User Group');
export const processItem = Selector('.ListItem').withText('Process');
export const decisionItem = Selector('.ListItem').withText('Decision');
export const roleOption = (text) =>
  Selector('.Modal.is-visible .LabeledInput .label.after').withText(text);
export const carbonRoleOption = (text) =>
  Selector('.Modal.is-visible .cds--radio-button-wrapper').withText(text);
export const userList = Selector('.UserList');
export const addUserModal = Selector('.AddUserModal');
export const logoutButton = Selector('header button').withText('Logout');
export const usernameDropdown = Selector('header button').withAttribute('aria-label', 'Open User');
export const searchField = Selector('.cds--search-input');
export const selectAllCheckbox = Selector('.Table thead .cds--table-column-checkbox label');
export const itemCheckbox = (idx) =>
  Selector('.Table tbody tr').nth(idx).find('.cds--table-column-checkbox label');
