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
export const edit = Selector('.ListItemSingleAction').withText('Edit');
export const copy = Selector('.ListItemSingleAction').withText('Copy');
export const del = Selector('.ListItemSingleAction').withText('Delete');
export const contextMenu = (entity) => entity.find('button.cds--overflow-menu');
export const listItemTrigger = (entity, trigger) =>
  entity
    .find('td .cds--tooltip-content')
    .withText(trigger)
    .parent('.cds--popover-container')
    .find('button.ListItemAction');
export const selectAllCheckbox = Selector('thead .cds--checkbox--inline');
export const bulkDelete = Selector('.cds--action-list button').withText('Delete');
export const listItem = (type, viewerMode = false) =>
  Selector(`.EntityList tbody tr td:nth-child(${viewerMode ? 1 : 2}) span`)
    .withText(new RegExp(type, 'i'))
    .parent('tr');
export const listItemWithText = (text) =>
  Selector('.EntityList tbody tr td:nth-child(2)').withText(text).parent('tr');
export const listItemLink = (type, viewerMode = false) =>
  listItem(type, viewerMode).find(`td:nth-child(${viewerMode ? 1 : 2}) a`);
export const listItemCheckbox = (item) => item.find('.cds--checkbox-label');
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
export const processItem = listItemLink('process', true);
export const templateOption = (text) => Selector('.Modal .templateContainer button').withText(text);
export const collectionsPage = Selector('a[href="#/collections"]');
