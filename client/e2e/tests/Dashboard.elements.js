/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const body = Selector('body');
export const dashboard = Selector('.ListItem.dashboard');
export const editButton = Selector('.edit-button');
export const reportEditButton = Selector('.EditButton');
export const reportDeleteButton = Selector('.DeleteButton');
export const reportResizeHandle = Selector('.react-resizable-handle');
export const report = Selector('.ReportRenderer');
export const nameEditField = Selector('.EntityNameForm .name-input');
export const dashboardName = Selector('.DashboardView .name');
export const addButton = Selector('.AddButton');
export const externalSourceLink = Selector('.Button').withText('External Website');
export const externalSourceInput = Selector('.externalInput');
export const addTileButton = Selector('.ReportModal button').withText('Add Tile');
export const textReportLink = Selector('.Button').withText('Text');
export const textReportInput = Selector('.editor');
export const textReportToolButton = (title) => Selector(`.Button[title=${title}]`);
export const textReportInsertDropdown = Selector('.InsertOptions');
export const textReportUrlInput = Selector('.InsertModal input').nth(0);
export const textReportAltInput = Selector('.InsertModal input').nth(1);
export const textReportInsertAddButton = Selector('.InsertModal .Button').withText('Add');
export const blankReportButton = Selector('.Button').withText('Blank report');
export const externalReport = Selector('iframe', {timeout: 60000});
export const textReport = Selector('.TextReport .editor');
export const textReportField = (element) => textReport.find(element);
export const exampleHeading = Selector('h1');
export const fullscreenButton = Selector('.fullscreen-button');
export const header = Selector('.cds--header');
export const themeButton = Selector('.theme-toggle');
export const fullscreenContent = Selector('.fullscreen');
export const shareButton = Selector('.share-button .Button');
export const shareSwitch = Selector('.ShareEntity .Switch');
export const shareFilterCheckbox = Selector('.ShareEntity .includeFilters input');
export const shareUrl = Selector('.ShareEntity .linkText');
export const shareOptimizeIcon = Selector('.Sharing.compact .iconLink');
export const shareHeader = Selector('.Sharing .header');
export const shareTitle = shareHeader.find('.name-container');
export const shareLink = shareHeader.find('.title-button');
export const deleteButton = Selector('.delete-button');
export const autoRefreshButton = Selector('.tools .Dropdown').withText('Auto Refresh');
export const modalConfirmbutton = Selector('.Modal .confirm.Button');
export const reportModal = Selector('.ReportModal');
export const reportModalConfirmButton = Selector('.ReportModal button.confirm');
export const reportModalOptionsButton = Selector('.ReportModal .optionsButton');
export const reportModalDropdownOption = Selector('.ReportModal .DropdownOption');
export const addFilterButton = Selector('.Button').withText('Add a filter');
export const option = (text) => Selector('.DropdownOption').withText(text);
export const instanceStateFilter = Selector('.InstanceStateFilter .Popover .Button');
export const selectionFilter = Selector('.SelectionFilter .Popover .Button');
export const switchElement = (text) => Selector('.Switch').withText(text);
export const dashboardContainer = Selector('.Dashboard');
export const templateModalProcessField = Selector('.TemplateModal .MultiSelect');
export const templateModalProcessTag = Selector('.TemplateModal .Tag');
export const templateOption = (text) =>
  Selector('.CarbonModal .templateContainer .Button').withText(text);
export const reportTile = Selector('.OptimizeReport');
export const customValueAddButton = Selector('.customValueAddButton');
export const typeahead = Selector('.Typeahead');
export const typeaheadInput = Selector('.Typeahead .Input');
export const typeaheadOption = (text) => typeahead.find('.DropdownOption').withText(text);
export const alertsDropdown = Selector('.AlertsDropdown .Button');
export const alertDeleteButton = Selector('.AlertModal .deleteButton');
export const collectionLink = Selector('.NavItem a').withText('New Collection');
export const notificationCloseButton = Selector('.Notification .close');
