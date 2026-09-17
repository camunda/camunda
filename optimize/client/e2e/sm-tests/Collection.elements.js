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
export const addButton = activeTab.find('.entityToolbarAction .cds--btn--primary');
export const emptyStateAdd = activeTab.find('.EmptyState .cds--btn--primary');
export const checkbox = (text) => Selector('.Checklist tr').withText(text);
export const processItem = listItem('process');
export const userName = (entity) => entity.find('.rowName');
export const carbonRoleOption = (text) =>
  Selector('.Modal.is-visible .cds--radio-button-wrapper').withText(text);
export const userList = Selector('.UserList');
// The design system portals its user menu to <body>, so the log-out entry sits outside <header>.
export const logoutButton = Selector('[data-slot="user-menu-content"] [role="menuitem"]').withText(
  /log\s?out/i
);
export const usernameDropdown = Selector('[data-slot="user-menu-trigger"]');
export const sourceModalSearchField = Selector('.SourcesModal .cds--search-input');
export const selectAllCheckbox = Selector('.Table thead .cds--table-column-checkbox label');
export const itemCheckbox = (idx) =>
  Selector('.Table tbody tr').nth(idx).find('.cds--table-column-checkbox label');
export const bulkRemove = activeTab.find('.entityToolbarAction button').withText('Remove');
