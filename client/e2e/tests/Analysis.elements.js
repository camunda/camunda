/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const flowNode = (id) => Selector(`[data-element-id="${id}"]`);
export const statisticsDiagram = Selector('.Statistics canvas');
export const endEventOverlay = Selector('.DiagramBehavior__end-event-statistics');
export const gatewayCancelButton = Selector('[name="gateway"] button');
export const gatewayInput = Selector('[name="gateway"] .SelectionPreview');
export const endEventInput = Selector('[name="endEvent"] .SelectionPreview');
export const branchAnalysisLink = Selector('.NavItem').withText('Branch analysis');
export const heatmapEl = Selector('svg .viewport image');
export const chart = Selector('.DurationChart canvas');
export const variablesTable = Selector('.VariablesTable.Table');
export const variablesTableRow = (text) => variablesTable.find('tr').withText(text);
export const outliersTable = Selector('.OutlierDetailsTable');
export const outliersTableRow = (text) => outliersTable.find('tr').withText(text);
export const outliersTableDetailsButton = (text) =>
  outliersTableRow(text).find('button').withText('View details');
export const filtersDropdown = Selector('.filterHeader .MenuDropdown');
export const warningMessage = Selector('.MessageBox--warning').withText(
  'No data shown due to incompatible filters'
);
