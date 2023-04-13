/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const singleReport = (text) =>
  Selector('.TypeaheadMultipleSelection__valueListItem').withExactText(text).find('input');
export const report = (title) => Selector('.ListItem.report').withText(title);
export const editButton = (report) => report.find('.DropdownOption').withText('Edit');
export const chartRenderer = Selector('.ChartRenderer');
export const reportColorPopover = (text) =>
  Selector('.TypeaheadMultipleSelection__valueListItem').withText(text).find('.Popover');
export const redColor = Selector('.color[color="#DB3E00"]');
export const configurationButton = Selector('.Configuration .Popover');
export const goalSwitch = Selector('.Configuration .Popover fieldset')
  .withText('Set Target')
  .find('.Switch');
export const goalInput = Selector('.Configuration .Popover fieldset')
  .withText('Set Target')
  .find('.Input[type="number"]');

export const dragEndIndicator = Selector('.endIndicator');
