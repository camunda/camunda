/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const nameEditField = Selector('.EntityNameForm .name-input');
export const reportRenderer = Selector('.ReportRenderer');
export const decisionTable = Selector('.DecisionTable');
export const decisionTableCell = (row, column) =>
  Selector('.DecisionTable tbody tr').nth(row).find('td').nth(column);
export const primaryModalButton = Selector('.Modal .Modal__actions .primary');
export const controlPanel = Selector('.ReportControlPanel');
export const filterButton = Selector('.Filter__dropdown .activateButton');
export const filterOption = (text) => Selector('.Filter .DropdownOption').withText(text);
export const reportTable = reportRenderer.find('.Table');
export const visualizationDropdown = Selector('.label').withText('Visualization').nextSibling();
export const option = (text) => Selector('.DropdownOption').withText(text);
export const reportChart = reportRenderer.find('canvas');
export const configurationButton = Selector('.Configuration .Popover');
export const gradientBarsSwitch = Selector('.Popover label').withText('Show Gradient Bars');
export const reportNumber = reportRenderer.find('.Number');
export const report = Selector('.Report');
export const modalDecisionTable = Selector('.DiagramModal .DMNDiagram');
