/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Selector} from 'testcafe';

import {overflowMenuOption, listItem} from './Common.elements';

export const collectionTitle = Selector('.Collection h2');
export const collectionBreadcrumb = Selector('.cds--header__menu-bar .breadcrumb');
export const collectionContextMenu = Selector('.Collection .cds--overflow-menu__wrapper button');
export const editCollectionNameButton = overflowMenuOption('Edit');
export const copyCollectionButton = overflowMenuOption('Copy');
export const deleteCollectionButton = overflowMenuOption('Delete');
export const remove = (element) => element.find('.DropdownOption').withText('Remove');
const tabButton = Selector('.Collection .cds--tabs__nav-item');
export const entityTab = tabButton.withText('Dashboards & reports');
export const entitiesTab = tabButton.withText('Dashboards');
export const userTab = tabButton.withText('Users');
export const alertTab = tabButton.withText('Alerts');
export const sourcesTab = tabButton.withText('Data sources');
export const activeTab = Selector('.Collection .cds--tab-content:not([hidden])');
export const addButton = activeTab.find('.cds--toolbar-content > .cds--btn--primary');
export const emptyStateAdd = activeTab.find('.EmptyState .cds--btn--primary');
export const checkbox = (text) => Selector('.Checklist tr').withText(text);
export const processItem = listItem('process');
export const userName = (entity) => entity.find('td:nth-child(2) .cds--stack-vertical').child(0);
export const carbonRoleOption = (text) =>
  Selector('.Modal.is-visible .cds--radio-button-wrapper').withText(text);
export const userList = Selector('.UserList');
export const logoutButton = Selector('header button').withText('Logout');
export const usernameDropdown = Selector('header button').withAttribute('aria-label', 'Open User');
export const sourceModalSearchField = Selector('.SourcesModal .cds--search-input');
export const selectAllCheckbox = Selector('.Table thead .cds--table-column-checkbox label');
export const itemCheckbox = (idx) =>
  Selector('.Table tbody tr').nth(idx).find('.cds--table-column-checkbox label');
export const bulkRemove = activeTab.find('.cds--action-list button').withText('Remove');
