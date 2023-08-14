/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const createNewButton = Selector('.CreateNewButton');
export const menu = (text) => Selector('ul').withAttribute('aria-label', text);
export const editButton = Selector('.edit-button');
export const confirmButton = Selector('button.confirm');
export const modalConfirmButton = Selector('.Modal.is-visible')
  .nth(-1)
  .find('.cds--modal-footer .cds--btn:last-child');
export const descriptionField = Selector('.EntityDescription');
export const descriptionParagraph = descriptionField.find('p');
export const addDescriptionButton = descriptionField.find('button');
export const showLessMoreDescriptionButton = descriptionField.find('button.toggle');
export const descriptionModal = Selector('.EntityDescriptionEditModal');
export const descriptionModalInput = descriptionModal.find('textarea');
export const modalNameInput = Selector('.Modal.is-visible input[type="text"]');
export const option = (text) => Selector('.DropdownOption').withText(text);
export const carbonOption = (text) => Selector('.cds--list-box__menu-item').withText(text);
export const menuOption = (text) => Selector('.cds--menu-item').withText(text);
export const submenuOption = (text) => Selector('.cds--menu-item > .cds--menu > *').withText(text);
export const selectedOption = (text) =>
  Selector('.cds--list-box__menu-item--active').withText(text);
export const entityList = Selector('.EntityList');
export const edit = (element) => element.find('.DropdownOption').withText('Edit');
export const copy = (element) => element.find('.DropdownOption').withText('Copy');
export const del = (element) => element.find('.DropdownOption').withText('Delete');
export const contextMenu = (entity) => entity.find('.Dropdown');
export const selectAllCheckbox = Selector('.columnHeaders > input[type="checkbox"]');
export const bulkMenu = Selector('.bulkMenu');
export const listItem = Selector('.ListItem');
export const dashboardItem = listItem.filter(
  (node) => node.querySelector('.name .type').textContent.indexOf('Dashboard') !== -1
);
export const reportItem = listItem.filter(
  (node) => node.querySelector('.name .type').textContent.indexOf('Report') !== -1
);
export const collectionItem = listItem.filter(
  (node) => node.querySelector('.name .type').textContent.indexOf('Collection') !== -1
);
export const processItem = listItem.filter(
  (node) => node.querySelector('.name .type').textContent.indexOf('Process') !== -1
);
export const reportLabel = reportItem.find('.name .type');
export const listItemCheckbox = (item) => item.find('input[type="checkbox"]');
export const newReportOption = Selector('.cds--menu--shown .cds--menu-item[aria-haspopup=true]');
export const templateModalProcessField = Selector('.Modal .DefinitionSelection input');
export const firstOption = Selector('.TemplateModal .cds--list-box__menu-item');
export const modalContainer = Selector('div:not([aria-hidden="true"]) > .cds--modal-container');
export const controlPanel = Selector('.ReportControlPanel');
export const nameEditField = Selector('.EntityNameForm .name-input');
export const typeahead = Selector('.Typeahead');
export const typeaheadOption = (text) => typeahead.find('.DropdownOption').withText(text);
export const notification = Selector('.Notification');
export const notificationCloseButton = notification.find('.close');
export const shareButton = Selector('.share-button .Button');
export const shareSwitch = Selector('.ShareEntity .Switch');
export const shareHeader = Selector('.Sharing .header');
export const shareTitle = shareHeader.find('.name-container');
export const shareLink = shareHeader.find('.title-button');
export const shareUrl = Selector('.ShareEntity .linkText');
export const shareOptimizeIcon = Selector('.Sharing.compact .iconLink');
export const addButton = Selector('.AddButton');
export const deleteButton = Selector('.delete-button');
export const usersTypeahead = Selector('.MultiUserInput input');
