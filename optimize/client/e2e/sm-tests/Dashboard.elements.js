/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Selector} from 'testcafe';

export const body = Selector('body');
export const dashboard = Selector('.ListItem.dashboard');
export const reportEditButton = Selector('.EditButton');
export const reportDeleteButton = Selector('.DeleteButton');
export const reportCopyButton = Selector('.CopyButton');
export const reportResizeHandle = Selector('.react-resizable-handle');
export const dashboardName = Selector('.DashboardView .name');
export const externalSourceLink = Selector('button').withText('External website');
export const externalSourceInput = Selector('.externalInput');
export const addTileButton = Selector('.CreateTileModal button').withText('Add tile');
export const textReportLink = Selector('button').withText('Text');
export const textReportInput = Selector('.editor');
export const textReportToolButton = (text) =>
  Selector('.toolbar .cds--popover-container').withText(text);
export const textReportInsertDropdown = Selector('.InsertOptions');
export const textReportUrlInput = Selector('.InsertModal input').nth(0);
export const textReportAltInput = Selector('.InsertModal input').nth(1);
export const textReportInsertAddButton = Selector('.InsertModal button').withText('Add');
export const blankReportButton = Selector('button').withText('Blank report');
export const externalReport = Selector('iframe', {timeout: 60000});
export const textReport = Selector('.TextTile .editor');
export const textReportField = (element) => textReport.find(element);
export const exampleHeading = Selector('h1');
export const fullscreenButton = Selector('.fullscreen-button');
export const header = Selector('.cds--header');
export const themeButton = Selector('.theme-toggle');
export const fullscreenContent = Selector('.fullscreen');
export const shareFilterCheckbox = Selector('.ShareEntity .shareFilterCheckbox');
export const autoRefreshButton = Selector('.tools .AutoRefreshSelect button');
export const createTileModal = Selector('.CreateTileModal');
export const createTileModalReportOptions = createTileModal.find('#addReportSelector');
export const addFilterButton = Selector('.AddFiltersButton').withText('Add a filter');
export const instanceStateFilter = Selector('.InstanceStateFilter .Popover button');
export const selectionFilter = Selector('.SelectionFilter .Popover .ListBoxTrigger');
export const switchElement = (text) => Selector('.Switch').withText(text);
export const toggleElement = (text) => Selector('.cds--toggle__label').withText(text);
export const dashboardContainer = Selector('.Dashboard');
export const templateModalProcessTag = Selector('.TemplateModal .Tag');
export const reportTile = Selector('.OptimizeReportTile');
export const textTile = Selector('.TextTile');
export const externalUrlTile = Selector('.ExternalUrlTile');
export const customValueAddButton = Selector('.customValueAddButton');
export const alertsDropdown = Selector('.AlertsDropdown button');
export const alertDeleteButton = Selector('.AlertModal .deleteAlertButton');
export const dashboardsLink = Selector('.NavItem a').withText('Dashboards');
export const createCopyButton = Selector('.create-copy');
