/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const createNewMenu = Selector('.CreateNewButton');
export const newReportOption = Selector('.CreateNewButton .Submenu');
export const option = text => Selector('.DropdownOption').withText(text);
export const collectionOption = text =>
  Selector('.CollectionsDropdown.is-open .DropdownOption').withText(text);
export const submenuOption = text => Selector('.Submenu .DropdownOption').withText(text);
export const homepageLink = Selector('.NavItem').withText('Dashboards & Reports');
export const reportItem = Selector('.ListItem.report');
export const dashboardItem = Selector('.ListItem.dashboard');
export const collectionItem = Selector('.ListItem.collection');
export const edit = element => element.find('.DropdownOption').withText('Edit');
export const setupNotice = Selector('.SetupNotice');
export const reportControlPanel = Selector('.ReportControlPanel');
export const editButton = Selector('.edit-button');
export const addButton = Selector('.AddButton');
export const reportLabel = Selector('.ListItemSection.name .type');
export const contextMenu = entity => entity.find('.contextMenu .Dropdown');
