/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const createNewMenu = Selector('.CreateNewButton');
export const newReportOption = Selector('.CreateNewButton .Submenu');
export const option = (text) => Selector('.DropdownOption').withText(text);
export const submenuOption = (text) => Selector('.Submenu .DropdownOption').withText(text);
export const homepageLink = Selector('.NavItem a').withText('Home');
export const reportItem = Selector('.ListItem').filter(
  (node) => node.querySelector('.name .type').textContent.indexOf('Report') !== -1
);
export const dashboardItem = Selector('.ListItem').filter(
  (node) => node.querySelector('.name .type').textContent.indexOf('Dashboard') !== -1
);
export const collectionItem = Selector('.ListItem').filter(
  (node) => node.querySelector('.name .type').textContent.indexOf('Collection') !== -1
);
export const edit = (element) => element.find('.DropdownOption').withText('Edit');
export const copy = (element) => element.find('.DropdownOption').withText('Copy');
export const del = (element) => element.find('.DropdownOption').withText('Delete');
export const noDataNotice = Selector('.NoDataNotice');
export const reportControlPanel = Selector('.ReportControlPanel');
export const editButton = Selector('.edit-button');
export const addButton = Selector('.AddButton');
export const reportLabel = reportItem.find('.name .type');
export const contextMenu = (entity) => entity.find('.Dropdown');
export const dashboardReportLink = Selector('.OptimizeReport .EntityName a');
export const breadcrumb = (text) => Selector('.HeaderNav a').withText(text);
export const dashboardView = Selector('.DashboardView');
export const modalNameInput = Selector('.Modal input[type="text"]');
export const confirmButton = Selector('.confirm.Button');
export const searchButton = Selector('.SearchField .Button');
export const searchField = Selector('.SearchField input');
export const moveCopySwitch = Selector('.moveSection .Switch');
export const copyTargetsInput = Selector('.Modal .Typeahead .Input');
export const copyTarget = (text) => Selector('.Modal .Typeahead .OptionsList').withText(text);
export const entityList = Selector('.EntityList');
export const copyModal = Selector('.CopyModal');
export const processTypeahead = Selector('.Modal .Typeahead');
export const firstTypeaheadOption = Selector('.Modal .DropdownOption');
export const templateModalNameField = Selector('.Modal .FormGroup .Input');
export const templateModalProcessField = Selector('.Modal .MultiSelect');
export const templateOption = (text) =>
  Selector('.Modal .templateContainer .Button').withText(text);
export const modalConfirmbutton = Selector('.Modal .confirm.Button');
export const selectAllCheckbox = Selector('.columnHeaders > input[type="checkbox"]');
export const listItemCheckbox = (item) => item.find('input[type="checkbox"]');
export const bulkMenu = Selector('.bulkMenu');
export const bulkDelete = Selector('.bulkMenu .DropdownOption');
export const listItem = Selector('.ListItem');
export const definitionSelection = Selector('.DefinitionSelection');
