/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const createNewMenu = Selector('.createAllButton');
export const newReportOption = Selector('.createAllButton .Submenu');
export const option = text => Selector('.DropdownOption').withText(text);
export const collectionOption = text =>
  Selector('.CollectionsDropdown.is-open .DropdownOption').withText(text);
export const submenuOption = text => Selector('.Submenu .DropdownOption').withText(text);
export const homepageLink = Selector('.HeaderNav__item').withText('Dashboards & Reports');
export const reportItem = Selector('.ReportItem');
export const dashboardItem = Selector('.DashboardItem');
export const collectionItem = Selector('.CollectionItem');
export const createDashboardButton = Selector('.createDashboard .Button');
export const duplicate = text => Selector(`button[title="Duplicate ${text}"]`);
export const edit = text => Selector(`a[title="Edit ${text}"]`);
export const showAll = type => Selector(`.${type} button`).withText('Show all');
export const setupNotice = Selector('.SetupNotice');
export const reportControlPanel = Selector('.ReportControlPanel');
export const editButton = Selector('.edit-button');
export const addButton = Selector('.AddButton');
export const createCollectionButton = Selector('.Modal button').withText('Create Collection');
export const collectionsDropdownFor = type => Selector(`.${type}Item .CollectionsDropdown`);
export const dashboardInCollection = Selector('.CollectionItem .DashboardItem');
export const reportLabel = Selector('.ReportItem .dataTitle span');
