/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Selector} from 'testcafe';

export const createNewButton = Selector('.CreateNewButton');
export const menu = (text) => Selector('ul').withAttribute('aria-label', text);
export const editButton = Selector('.edit-button');
export const confirmButton = Selector('button.confirm');
export const modalConfirmButton = Selector('.Modal.is-visible')
  .nth(-1)
  .find('.cds--modal-footer .cds--btn:last-child:not([disabled])');
export const descriptionField = Selector('.EntityDescription');
export const descriptionParagraph = descriptionField.find('p');
export const addDescriptionButton = descriptionField.find('button');
export const showLessMoreDescriptionButton = descriptionField.find('button.toggle');
export const descriptionModal = Selector('.EntityDescriptionEditModal');
export const descriptionModalInput = descriptionModal.find('textarea');
export const modalNameInput = Selector('.Modal.is-visible input[type="text"]');
export const option = (text) => Selector('.DropdownOption').withText(text);
export const carbonOption = (text) => Selector('.cds--list-box__menu-item').withText(text);
export const menuOption = (text) => Selector('.cds--menu-item').filterVisible().withText(text);
export const submenuOption = (text) =>
  Selector('.cds--menu-item[aria-expanded=true] > .cds--menu > *').withText(text);
export const selectedOption = (text) =>
  Selector('.cds--list-box__menu-item--active').withText(text);
export const entityList = Selector('.EntityList');
// The design-system EntityList surfaces per-row actions in an overflow menu that portals to <body>.
export const edit = Selector('[data-slot="dropdown-menu-item"]').withText('Edit');
export const copy = Selector('[data-slot="dropdown-menu-item"]').withText('Copy');
export const del = Selector('[data-slot="dropdown-menu-item"]').withText('Delete');
export const contextMenu = (entity) => entity.find('[data-slot="data-table-actions-trigger"]');
// A single row action renders inline as a labelled button rather than inside the overflow menu.
export const listItemTrigger = (entity, trigger) =>
  entity.find('[data-slot="button"]').withText(trigger);
export const selectAllCheckbox = Selector('.EntityList thead [aria-label="Select all rows"]');
export const bulkDelete = Selector('.entityToolbarAction button').withText('Delete');
// The type label under the name identifies the row's entity kind regardless of column layout.
export const listItem = (type) =>
  Selector('.EntityList tbody tr .entityType').withText(new RegExp(type, 'i')).parent('tr');
export const listItemWithText = (text) =>
  Selector('.EntityList tbody tr .entityCell').withText(text).parent('tr');
export const listItemLink = (type) => listItem(type).find('a.entityName');
export const listItemCheckbox = (item) => item.find('[aria-label="Select row"]');
export const newReportOption = Selector('.cds--menu--shown .cds--menu-item[aria-haspopup=true]');
export const templateModalProcessField = Selector('.Modal .DefinitionSelection input');
export const firstOption = Selector('.TemplateModal .cds--list-box__menu-item');
export const modalContainer = Selector('div:not([aria-hidden="true"]) > .cds--modal-container');
export const controlPanel = Selector('.ReportControlPanel');
export const nameEditField = Selector('.EntityNameForm .name-input input');
export const typeahead = Selector('.Typeahead');
export const typeaheadOption = (text) => typeahead.find('.DropdownOption').withText(text);
export const notification = Selector('.Notification');
export const notificationCloseButton = notification.find(
  '.cds--actionable-notification__close-button'
);
export const shareButton = Selector('.share-button button');
export const shareSwitch = Selector('.ShareEntity .cds--toggle__switch');
export const shareHeader = Selector('.Sharing .header');
export const shareTitle = shareHeader.find('.name-container');
export const shareLink = shareHeader.find('.title-button');
export const shareUrl = Selector('.ShareEntity input[type="text"]');
export const shareOptimizeIcon = Selector('.Sharing.compact .iconLink');
export const addButton = Selector('.AddButton');
export const deleteButton = Selector('.delete-button');
export const usersTypeahead = Selector('.MultiUserInput input');
export const comboBox = Selector('.cds--combo-box input');
export const overflowMenuOptions = Selector('.cds--overflow-menu-options');
export const overflowMenuOption = (text) => overflowMenuOptions.find('button').withText(text);
export const toggleElement = (text) => Selector('.cds--toggle__label').withText(text);
export const radioButton = (text) => Selector('.cds--radio-button-wrapper').withText(text);
export const checkbox = (text) => Selector('.cds--checkbox-label').withText(text);
export const kpiFilterButton = Selector('.filterTile .actions > button');
export const kpiTemplateSelection = Selector('input#KpiSelectionComboBox');
export const emptyStateAdd = Selector('.EmptyState .cds--btn--primary');
export const processItem = listItemLink('process');
export const templateOption = (text) => Selector('.Modal .templateContainer button').withText(text);
// The design system renders the collections link as a sidebar item.
export const collectionsPage = Selector('[data-slot="app-sidebar-item"][href="#/collections"]');
