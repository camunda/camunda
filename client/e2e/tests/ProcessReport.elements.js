/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const nameEditField = Selector('.EntityNameForm .name-input');
export const reportName = Selector('.ReportView .name');
export const reportRenderer = Selector('.ReportRenderer');
export const report = Selector('.ReportItem');
export const editButton = Selector('a[title="Edit Report"]');
export const shareButton = Selector('.share-button');
export const shareSwitch = Selector('.ShareEntity .Switch');
export const shareUrl = Selector('.ShareEntity .ShareEntity__share-link input');
export const deleteButton = Selector('.delete-button');
export const modalConfirmbutton = Selector('.Modal .confirm.Button');
export const groupbyDropdown = Selector('.label')
  .withText('Group By')
  .nextSibling();
export const groupbyDropdownButton = groupbyDropdown.find('button');
export const visualizationDropdown = Selector('.label')
  .withText('Visualization')
  .nextSibling();
export const option = text => Selector('.DropdownOption').withText(text);
export const reportTable = reportRenderer.find('.Table');
export const reportChart = reportRenderer.find('canvas');
export const reportDiagram = reportRenderer.find('svg');
export const reportNumber = reportRenderer.find('.Number');
export const instanceCount = reportRenderer.find('.additionalInfo');
export const reportProgressBar = reportRenderer.find('.ProgressBar');
export const configurationButton = Selector('.Configuration .Popover');
export const resetButton = Selector('.resetButton');
export const goalSwitch = Selector('.Configuration .Popover fieldset')
  .withText('Goal')
  .find('.Switch');
export const flowNode = id => reportRenderer.find(`[data-element-id="${id}"]`);
export const tooltip = reportRenderer.find('.Tooltip');
export const targetValueButton = Selector('.toggleButton');
export const targetValueInput = name =>
  Selector('.Modal .rt-tr')
    .withText(name)
    .find('.Input');
export const primaryModalButton = Selector('.Modal .Modal__actions .primary');
export const warning = Selector('.Message--warning');
export const processPartButton = Selector('.ReportControlPanel .Button').withText(
  'Process Instance Part'
);
export const modalFlowNode = id => Selector(`.Modal [data-element-id="${id}"]`);
export const columnSwitch = label =>
  Selector('.ColumnSelection__entry')
    .withText(label)
    .find('.Switch');
export const tableHeader = idx => Selector('.rt-thead.-header .rt-th').nth(idx);
export const tableCell = (row, column) =>
  Selector('.rt-tbody .rt-tr')
    .nth(row)
    .find('.rt-td')
    .nth(column);
export const tooltipSwitch = Selector('.RelativeAbsoluteSelection .Switch');
export const instanceCountSwitch = Selector('.Configuration .Switch');
export const filterButton = Selector('.Filter__dropdown .activateButton');
export const filterOption = text => Selector('.Filter .DropdownOption').withText(text);
export const variableFilterTypeahead = Selector('.Modal__content .Typeahead');
export const variableFilterTypeaheadOption = text =>
  Selector('.Modal__content .Typeahead .DropdownOption').withText(text);
export const variableFilterOperatorButton = text =>
  Selector('.Modal .VariableFilter__buttonRow .Button').withText(text);
export const variableFilterValueInput = Selector('.Modal .VariableFilter__valueFields input');
