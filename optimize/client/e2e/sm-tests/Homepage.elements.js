/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Selector} from 'testcafe';

export const createNewDashboardButton = Selector('button').withText('Create new dashboard');
export const emptyStateComponent = Selector('.EmptyState');
export const blankDashboardButton = Selector('button').withText('Blank dashboard');
export const noDataNotice = Selector('.NoDataNotice');
export const dashboardReportLink = Selector('.OptimizeReportTile .EntityName a');
export const breadcrumb = (text) => Selector('.cds--header__menu-bar a').withText(text);
export const dashboardView = Selector('.DashboardView');
export const searchField = Selector('input.cds--search-input');
export const copyTargetsInput = Selector('.CopyModal .cds--combo-box .cds--text-input');
export const copyModal = Selector('.CopyModal');
export const definitionSelection = Selector('.DefinitionSelection');
