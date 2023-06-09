/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const decisionTable = Selector('.DecisionTable');
export const decisionTableCell = (row, column) =>
  Selector('.DecisionTable tbody tr').nth(row).find('td').nth(column);
export const filterButton = Selector('.Filter__dropdown .activateButton');
export const filterOption = (text) => Selector('.Filter .DropdownOption').withText(text);
export const visualizationDropdown = Selector('.label').withText('Visualization').nextSibling();
export const configurationButton = Selector('.configurationPopover .Button');
export const gradientBarsSwitch = Selector('.configurationPopover label').withText(
  'Show Gradient Bars'
);
export const report = Selector('.Report');
export const modalDecisionTable = Selector('.DiagramModal .DMNDiagram');
export const sectionToggle = (sectionName) =>
  Selector('.ReportControlPanel .sectionTitle')
    .withText(new RegExp(sectionName, 'i')) // we are using CSS text-transform uppercase, which is handled inconsistently across browsers: https://github.com/DevExpress/testcafe/issues/3335
    .find('.sectionToggle');
