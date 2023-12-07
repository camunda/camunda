/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const createNewDashboardButton = Selector('button').withText('Create new dashboard');
export const emptyStateComponent = Selector('.EmptyState');
export const blankDashboardButton = Selector('.Button').withText('Blank dashboard');
export const homepageLink = Selector('.NavItem a').withText('Collections');
export const noDataNotice = Selector('.NoDataNotice');
export const dashboardReportLink = Selector('.OptimizeReportTile .EntityName a');
export const breadcrumb = (text) => Selector('.cds--header__menu-bar a').withText(text);
export const dashboardView = Selector('.DashboardView');
export const searchButton = Selector('.SearchField .Button');
export const searchField = Selector('.SearchField input');
export const moveCopySwitch = Selector('.CopyModal .cds--toggle');
export const copyTargetsInput = Selector('.CopyModal .cds--combo-box .cds--text-input');
export const copyModal = Selector('.CopyModal');
export const bulkDelete = Selector('.bulkMenu .DropdownOption');
export const definitionSelection = Selector('.DefinitionSelection');
