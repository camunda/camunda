/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const createNewMenu = Selector('.CreateNewButton');
export const newReportOption = Selector('.CreateNewButton .Submenu');
export const option = text => Selector('.DropdownOption').withText(text);
export const submenuOption = text => Selector('.Submenu .DropdownOption').withText(text);
export const homepageLink = Selector('.NavItem a').withText('Home');
export const reportItem = Selector('.ListItem.report');
export const dashboardItem = Selector('.ListItem.dashboard');
export const collectionItem = Selector('.ListItem.collection');
export const edit = element => element.find('.DropdownOption').withText('Edit');
export const copy = element => element.find('.DropdownOption').withText('Copy');
export const setupNotice = Selector('.SetupNotice');
export const reportControlPanel = Selector('.ReportControlPanel');
export const editButton = Selector('.edit-button');
export const addButton = Selector('.AddButton');
export const reportLabel = Selector('.ListItem.report .ListItemSection.name .type');
export const contextMenu = entity => entity.find('.contextMenu .Dropdown');
export const dashboardReportLink = Selector('.OptimizeReport__heading');
export const dashboardBreadcrumb = Selector('.HeaderNav a').withText('New Dashboard');
export const dashboardView = Selector('.DashboardView');
export const modalNameInput = Selector('.Modal input[type="text"]');
export const confirmButton = Selector('.confirm.Button');
export const searchField = Selector('.searchContainer input');
export const moveCopySwitch = Selector('.moveSection .Switch');
export const copyTargetsInput = Selector('.Modal .Typeahead .Input');
export const copyTarget = text => Selector('.Modal .Typeahead .searchResult').withText(text);
