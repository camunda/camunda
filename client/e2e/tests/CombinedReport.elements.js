/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const singleReport = text =>
  Selector('.TypeaheadMultipleSelection__valueListItem')
    .withExactText(text)
    .find('input');
export const report = title => Selector('.ListItem.report').withText(title);
export const editButton = report => report.find('.DropdownOption').withText('Edit');
export const reportRenderer = Selector('.ReportRenderer');
export const chartRenderer = Selector('.ChartRenderer');
export const reportTable = reportRenderer.find('.Table');
export const reportChart = reportRenderer.find('canvas');
export const reportColorPopover = text =>
  Selector('.TypeaheadMultipleSelection__valueListItem')
    .withText(text)
    .find('.Popover');
export const redColor = Selector('.color[color="#DB3E00"]');
export const configurationButton = Selector('.Configuration .Popover');
export const goalSwitch = Selector('.Configuration .Popover fieldset')
  .withText('Goal')
  .find('.Switch');
export const goalInput = Selector('.Configuration .Popover fieldset')
  .withText('Goal')
  .find('.Input');

export const dragEndIndicator = Selector('.endIndicator');
