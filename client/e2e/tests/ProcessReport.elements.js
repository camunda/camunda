/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const nameEditField = Selector('.EntityNameForm .name-input');
export const reportName = Selector('.ReportView .name');
export const reportRenderer = Selector('.ReportRenderer');
export const report = Selector('.ListItem.report');
export const editButton = Selector('.DropdownOption').withText('Edit');
export const shareButton = Selector('.share-button > .Button');
export const shareSwitch = Selector('.ShareEntity .Switch');
export const shareUrl = Selector('.ShareEntity .shareLink input');
export const shareHeader = Selector('.Sharing__title');
export const deleteButton = Selector('.delete-button');
export const modalConfirmbutton = Selector('.Modal .confirm.Button');
export const groupbyDropdown = Selector('.label')
  .withText('Group By')
  .nextSibling();
export const groupbyDropdownButton = groupbyDropdown.find('button');
export const visualizationDropdown = Selector('.label')
  .withText('Visualization')
  .nextSibling();
export const option = text => Selector('.DropdownOption').withExactText(text);
export const configurationOption = text =>
  Selector('.Configuration .DropdownOption').withExactText(text);
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
export const goalTargetInput = Selector('.Configuration .Popover fieldset')
  .withText('Goal')
  .find('.LabeledInput')
  .nth(1)
  .find('input');
export const dropdownOption = text => Selector('.Dropdown.is-open .DropdownOption').withText(text);
export const flowNode = id => Selector(`.BPMNDiagram [data-element-id="${id}"]`);
export const tooltip = reportRenderer.find('.Tooltip');
export const targetValueButton = Selector('.toggleButton');
export const targetValueInput = name =>
  Selector('.Modal .rt-tr')
    .withText(name)
    .find('.Input');
export const targetValueUnitSelect = name =>
  Selector('.Modal .rt-tr')
    .withText(name)
    .find('.Dropdown');
export const primaryModalButton = Selector('.Modal .Modal__actions .primary');
export const warning = Selector('.Message--warning');
export const controlPanel = Selector('.ReportControlPanel');
export const processPartButton = Selector('.ReportControlPanel .Button').withText(
  'Process Instance Part'
);
export const modalFlowNode = id => Selector(`.Modal [data-element-id="${id}"]`);
export const selectSwitchLabel = label => Selector('.Switch .label').withText(label);
export const tableHeader = idx => Selector('.rt-thead.-header .rt-th').nth(idx);
export const tableCell = (row, column) =>
  Selector('.rt-tbody .rt-tr')
    .nth(row)
    .find('.rt-td')
    .nth(column);
export const instanceCountSwitch = Selector('.Configuration .Switch');
export const filterButton = Selector('.Filter__dropdown .activateButton');
export const filterOption = text => Selector('.Filter .DropdownOption').withText(text);
export const collectionsDropdown = Selector(`.CollectionsDropdown`);
export const createCollectionButton = Selector('.Modal button').withText('Create Collection');
export const collectionOption = text =>
  Selector('.CollectionsDropdown.is-open .DropdownOption').withText(text);
export const limitPrecisionSwitch = Selector('.NumberConfig .Switch:first-child');
export const limitPrecisionInput = Selector('.precision input');
export const showFlowNodesSwitch = Selector('.VisibleNodesFilter .Switch');
export const showFlowNodes = Selector('.Button').withText('Select Flow Nodes...');
export const deselectAllButton = Selector('.Button').withText('Deselect All');
export const flowNodeStatusSelect = Selector('.NodeStatus .Select');
export const nodeTableCell = text => Selector('.rt-tbody .rt-td').withText(text);
export const distributedBySelect = Selector('legend')
  .withText('Distributed By')
  .nextSibling();
export const userTaskDurationSelect = Selector('.UserTaskDurationTime button');
export const cyanColor = Selector('div[color="#00bcd4"]');
export const axisInputs = label => Selector(`input[placeholder="${label}"]`);
export const chartGoalInput = Selector('input[placeholder="Goal value"]');
export const warningMessage = Selector('.Report .Message--warning');
export const controlPanelFilter = Selector('.ActionItem');
export const filterRemoveButton = controlPanelFilter.find('.Button');
export const definitionSelection = Selector('.Popover.DefinitionSelection');
export const definitionSelectionDialog = Selector('.Popover.DefinitionSelection .Popover__dialog');
export const versionPopover = Selector('.VersionPopover');
export const versionAll = Selector('input[type="radio"]').nth(0);
export const versionLatest = Selector('input[type="radio"]').nth(1);
export const versionSpecific = Selector('input[type="radio"]').nth(2);
export const versionCheckbox = number =>
  Selector('.specificVersions input[type="checkbox"]').nth(-number);
export const tenantPopover = Selector('.TenantPopover');
export const modalContainer = Selector('.Modal__content-container');
export const aggregationTypeSelect = Selector('.AggregationType .Select');
export const aggregationOption = text =>
  Selector('.AggregationType .DropdownOption').withText(text);
