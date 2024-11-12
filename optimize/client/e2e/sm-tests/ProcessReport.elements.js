/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Selector} from 'testcafe';

import * as Common from './Common.elements.js';

export const reportContainer = Selector('.Report');
export const reportName = Selector('.ReportView .name');
export const reportRenderer = Selector('.ReportRenderer').nth(0);
export const report = Selector('.ListItem.report');
export const definitionCopyButton = (definition) => definition.find('.actionBtn').nth(0);
export const definitionEditButton = (definition) => definition.find('.Popover button');
export const checkbox = (name) => Selector('.label').withText(name);
export const groupbyDropdown = checkbox('Group by').nextSibling();
export const viewDropdown = checkbox('View').nextSibling();
export const distributedBySelect = checkbox('and').nextSibling();
export const groupbyDropdownButton = groupbyDropdown.find('button');
export const removeGroupButton = Selector('.removeGrouping');
export const visualizationDropdown = Selector('.Select.Visualization button');
export const configurationOption = (text) =>
  Selector('.Configuration .DropdownOption').withExactText(text);
export const reportTable = reportRenderer.find('.Table');
export const reportChart = reportRenderer.find('canvas');
export const reportDiagram = reportRenderer.find('.djs-container > svg');
export const reportNumber = reportRenderer.find('.Number .data');
export const instanceCount = reportRenderer.find('.additionalInfo');
export const reportProgressBar = reportRenderer.find('.ProgressBar');
export const configurationButton = Selector('.configurationPopover button');
export const resetButton = Selector('.resetButton');
export const goalTargetInput = Selector('.configurationPopover fieldset')
  .withText('Set target')
  .find('input[type="number"]');
export const dropdownOption = (text) =>
  Selector('.Dropdown.is-open .DropdownOption').withText(text);
export const flowNode = (id) => Selector(`.BPMNDiagram [data-element-id="${id}"]`);
export const tooltip = reportRenderer.find('.Tooltip');
export const badge = (id) =>
  Selector(`.BPMNDiagram .djs-overlay-container [data-container-id="${id}"] .djs-overlay`);
export const targetValueButton = Selector('.toggleButton');
export const targetValueInput = (name) => Selector('.Modal tbody tr').withText(name).find('input');
export const nodeFilterOperator = (name) =>
  Selector('.Modal tbody tr').withText(name).find('.Select:first-child');
export const warning = Selector('.Message--warning');
export const processPartButton = Selector('.ReportControlPanel button').withText(
  'Process instance part'
);
export const modalFlowNode = (id) => Selector(`.ProcessPartModal [data-element-id="${id}"]`);
export const selectSectionWithLabel = (label) => Selector('section .sectionTitle').withText(label);
export const tableGroup = (idx) => Selector('.Table thead tr.groupRow th').nth(idx);
export const tableHeader = (idx) => Selector('.Table thead tr:last-child th.tableHeader').nth(idx);
export const tableCell = (row, column) =>
  Selector('.Table tbody tr').nth(row).find('td').nth(column);
export const instanceCountSwitch = Selector('.Configuration .ShowInstanceCount .cds--toggle');
export const filterButton = Selector('.Filter__dropdown button');
export const flowNodeFilterButton = Selector('.Filter__dropdown button').nth(1);
export const filterOption = (text) =>
  Selector('.Filter__dropdown.is-open .DropdownOption').withExactText(text);
export const subFilterOption = (text) =>
  Selector('.Filter__dropdown.is-open .Submenu .DropdownOption').withText(text);
export const modalOption = (text) =>
  Selector('.Modal.is-visible .cds--radio-button__label').withText(text);
export const collectionsDropdown = Selector(`.CollectionsDropdown`);
export const collectionOption = (text) =>
  Selector('.CollectionsDropdown.is-open .DropdownOption').withText(text);
export const limitPrecisionSwitch = Selector('.PrecisionConfig .cds--toggle').withText(
  'Custom precision'
);
export const limitPrecisionInput = Selector('.precision input');
export const flowNodeStatusSelect = Selector('.NodeStatus .Select');
export const nodeTableCell = (text) => Selector('.Table tbody td').withText(text);
export const cyanColor = Selector('div[color="#00bcd4"]');
export const axisInputs = (label) => Selector('.cds--form-item').withText(label).find('input');
export const warningMessage = Selector('.Report .cds--inline-notification');
export const controlPanelFilter = Selector('.ActionItem');
export const filterRemoveButton = controlPanelFilter.find('button:last-child');
export const definitionElement = (name) => Selector('.DefinitionList li').withText(name);
export const definitionEditorPopover = Selector('.DefinitionList .Popover');
export const definitionEditor = definitionEditorPopover.find('button');
export const definitionEditorDialog = definitionEditorPopover.find('.popoverContent');
export const versionPopover = Selector('.VersionPopover');
export const versionAll = Common.radioButton('All');
export const versionLatest = Common.radioButton('Always display latest');
export const versionSpecific = Common.radioButton('Specific versions');
export const versionCheckbox = (number) =>
  Selector('.specificVersions .cds--checkbox-label').nth(number);
export const tenantPopover = Selector('.TenantPopover');
export const aggregationTypeSelect = Selector('.AggregationType');
export const aggregationOption = (text) =>
  Selector('.AggregationType .cds--toggle__label').withText(text);
export const detailsPopoverButton = Selector('.EntityName .Popover button');
export const modalButton = (text) => Selector('.ReportDetails .modalButton').withText(text);
export const rawDataTable = Selector('.RawDataModal .Table');
export const modalDiagram = Selector('.DiagramModal .BPMNDiagram');
export const objectVariableModalCloseButton = Selector('.ObjectVariableModal .close');
export const rawDataModalCloseButton = Selector('.RawDataModal .close');
export const bucketSizeSwitch = Selector('.BucketSize .cds--toggle');
export const bucketSizeUnitSelect = Selector('.BucketSize .Select').nth(0);
export const nextPageButton = Selector('.Table .cds--pagination__button--forward');
export const rowsPerPageButton = Selector('.Table .cds--select-input');
export const rowsPerPageOption = (text) => Selector('.Table .cds--select-option').withText(text);
export const addMeasureButton = Selector('.addMeasure button');
export const removeMeasureButton = Selector('.Measure').nth(1).find('.SelectionPreview button');
export const heatDropdown = Selector('.Heatmap .Select');
export const sectionToggle = (sectionName) =>
  Selector('button.cds--accordion__heading').withText(sectionName);
export const deselectAllButton = Selector('button').withText('Deselect all');
export const addDefinitionButton = Selector('.AddDefinition');
export const definitionEntry = (name) => Selector('.Checklist tr').withText(name);
export const lineButton = Selector('.cds--radio-button-wrapper').withText('Line');
export const tableScrollableContainer = reportTable.find('table');
export const objectViewBtn = reportTable.find('.ObjectViewBtn').nth(0);
export const objectVariableModal = Selector('.ObjectVariableModal');
export const renameVariablesBtn = Selector('button').withText('Rename variables');
export const newNameInput = (name) =>
  Selector('.RenameVariablesModal tbody tr').withText(name).find('input');
export const numberReportInfo = reportRenderer.find('.Number .label');
export const variableSubmenuOption = (text) => Selector('.Submenu .DropdownOption').withText(text);
export const collapsibleContainer = Selector('.CollapsibleContainer');
export const collapsibleContainerTable = collapsibleContainer.find('.Table');
export const collapsibleContainerExpandButton = collapsibleContainer.find('.expandButton');
export const collapsibleContainerCollapseButton = collapsibleContainer.find('.collapseButton');
