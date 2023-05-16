/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const createNewDashboardButton = Selector('.Button').withText('Create New Dashboard');
export const emptyStateComponent = Selector('.EmptyState');
export const blankDashboardButton = Selector('.Button').withText('Blank Dashboard');
export const homepageLink = Selector('.NavItem a').withText('Collections');
export const noDataNotice = Selector('.NoDataNotice');
export const dashboardReportLink = Selector('.OptimizeReport .EntityName a');
export const breadcrumb = (text) => Selector('.cds--header__menu-bar a').withText(text);
export const dashboardView = Selector('.DashboardView');
export const searchButton = Selector('.SearchField .Button');
export const searchField = Selector('.SearchField input');
export const moveCopySwitch = Selector('.moveSection .Switch');
export const copyTargetsInput = Selector('.CopyModal .Typeahead .Input');
export const copyTarget = (text) => Selector('.CopyModal .Typeahead .OptionsList').withText(text);
export const copyModal = Selector('.CopyModal');
export const bulkDelete = Selector('.bulkMenu .DropdownOption');
export const definitionSelection = Selector('.DefinitionSelection');
