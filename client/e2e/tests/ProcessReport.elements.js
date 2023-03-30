/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const nameEditField = Selector('.EntityNameForm .name-input');
export const templateModalProcessField = Selector('.CarbonModal .MultiSelect');
export const templateOption = (text) =>
  Selector('.TemplateModal .templateContainer .Button').withText(text);
export const reportName = Selector('.ReportView .name');
export const reportRenderer = Selector('.ReportRenderer');
export const report = Selector('.ListItem.report');
export const editButton = Selector('.edit-button');
export const shareButton = Selector('.share-button .Button');
export const shareSwitch = Selector('.ShareEntity .Switch');
export const shareUrl = Selector('.ShareEntity .linkText');
export const shareHeader = Selector('.Sharing .header');
export const shareOptimizeIcon = Selector('.Sharing.compact .iconLink');
export const shareTitle = shareHeader.find('.name-container');
export const shareLink = shareHeader.find('.title-button');
export const deleteButton = Selector('.delete-button');
export const definitionCopyButton = (definition) => definition.find('.Button').nth(0);
export const definitionEditButton = (definition) => definition.find('.Popover .Button');
export const modalConfirmbutton = Selector('.Modal .confirm.Button');
export const checkbox = (name) => Selector('.label').withText(name);
export const groupbyDropdown = checkbox('Group By').nextSibling();
export const groupbyDropdownButton = groupbyDropdown.find('button');
export const removeGroupButton = Selector('.removeGrouping');
export const visualizationDropdown = checkbox('Visualization').nextSibling();
export const option = (text) => Selector('.DropdownOption').withExactText(text);
export const configurationOption = (text) =>
  Selector('.Configuration .DropdownOption').withExactText(text);
export const reportTable = reportRenderer.find('.Table');
export const reportChart = reportRenderer.find('canvas');
export const reportDiagram = reportRenderer.find('.djs-container > svg');
export const reportNumber = reportRenderer.find('.Number .data');
export const instanceCount = reportRenderer.find('.additionalInfo');
export const reportProgressBar = reportRenderer.find('.ProgressBar');
export const configurationButton = Selector('.Configuration .Popover .buttonWrapper button');
export const resetButton = Selector('.resetButton');
export const goalSwitch = Selector('.Configuration .Popover fieldset')
  .withText('Set Target')
  .find('.Switch');
export const goalTargetInput = Selector('.Configuration .Popover fieldset')
  .withText('Set Target')
  .find('.LabeledInput')
  .nth(1)
  .find('input');
export const dropdownOption = (text) =>
  Selector('.Dropdown.is-open .DropdownOption').withText(text);
export const flowNode = (id) => Selector(`.BPMNDiagram [data-element-id="${id}"]`);
export const tooltip = reportRenderer.find('.Tooltip');
export const badge = (id) =>
  Selector(`.BPMNDiagram .djs-overlay-container [data-container-id="${id}"] .djs-overlay`);
export const targetValueButton = Selector('.toggleButton');
export const targetValueInput = (name) => Selector('.Modal tbody tr').withText(name).find('.Input');
export const nodeFilterOperator = (name) =>
  Selector('.Modal tbody tr').withText(name).find('.Dropdown:first-child');
export const primaryModalButton = Selector('.Modal .Modal__actions .primary');
export const warning = Selector('.Message--warning');
export const controlPanel = Selector('.ReportControlPanel');
export const processPartButton = Selector('.ReportControlPanel .Button').withText(
  'Process Instance Part'
);
export const modalFlowNode = (id) => Selector(`.Modal [data-element-id="${id}"]`);
export const selectSectionWithLabel = (label) => Selector('section .sectionTitle').withText(label);
export const selectSwitchLabel = (label) => Selector('.Switch .label').withText(label);
export const tableGroup = (idx) => Selector('.Table thead tr.groupRow th').nth(idx);
export const tableHeader = (idx) => Selector('.Table thead tr:last-child th').nth(idx);
export const tableCell = (row, column) =>
  Selector('.Table tbody tr').nth(row).find('td').nth(column);
export const instanceCountSwitch = Selector('.Configuration .Switch');
export const filterButton = Selector('.Filter__dropdown .activateButton');
export const flowNodeFilterButton = Selector('.Filter__dropdown .activateButton').nth(1);
export const filterOption = (text) =>
  Selector('.Filter__dropdown.is-open .DropdownOption').withExactText(text);
export const subFilterOption = (text) =>
  Selector('.Filter__dropdown.is-open .Submenu .DropdownOption').withText(text);
export const modalOption = (text) =>
  Selector('.Modal__content label').withText(text).find('.Input');
export const collectionsDropdown = Selector(`.CollectionsDropdown`);
export const createCollectionButton = Selector('.Modal button').withText('Create Collection');
export const collectionOption = (text) =>
  Selector('.CollectionsDropdown.is-open .DropdownOption').withText(text);
export const limitPrecisionSwitch = Selector('.PrecisionConfig .Switch').withText(
  'Custom Precision'
);
export const limitPrecisionInput = Selector('.precision input');
export const flowNodeStatusSelect = Selector('.NodeStatus .Select');
export const nodeTableCell = (text) => Selector('.Table tbody td').withText(text);
export const distributedBySelect = Selector('.DistributedBy .Select');
export const cyanColor = Selector('div[color="#00bcd4"]');
export const axisInputs = (label) => Selector(`input[placeholder="${label}"]`);
export const chartGoalInput = Selector('input[placeholder="Goal value"]');
export const warningMessage = Selector('.Report .MessageBox--warning');
export const controlPanelFilter = Selector('.ActionItem');
export const filterRemoveButton = controlPanelFilter.find('.Button:last-child');
export const definitionElement = (name) => Selector('.DefinitionList li').withText(name);
export const definitionEditor = Selector('.DefinitionList .Popover .Button');
export const definitionSelectionDialog = Selector('.DefinitionList .dialog');
export const versionPopover = Selector('.VersionPopover');
export const versionAll = Selector('input[type="radio"]').nth(0);
export const versionLatest = Selector('input[type="radio"]').nth(1);
export const versionSpecific = Selector('input[type="radio"]').nth(2);
export const versionCheckbox = (number) =>
  Selector('.specificVersions input[type="checkbox"]').nth(number);
export const tenantPopover = Selector('.TenantPopover');
export const modalContainer = Selector('.Modal__content-container');
export const aggregationTypeSelect = Selector('.AggregationType');
export const aggregationOption = (text) => Selector('.AggregationType .Switch').withText(text);
export const detailsPopoverButton = Selector('.EntityName .Popover .Button');
export const modalButton = (text) => Selector('.ReportDetails .modalButton').withText(text);
export const rawDataTable = Selector('.RawDataModal .Table');
export const modalDiagram = Selector('.DiagramModal .BPMNDiagram');
export const closeModalButton = Selector('.Modal .Button').withText('Close');
export const bucketSizeSwitch = Selector('.BucketSize .Switch');
export const bucketSizeUnitSelect = Selector('.BucketSize .Select').nth(0);
export const submenuOption = (text) => Selector('.Submenu .DropdownOption').withText(text);
export const nextPageButton = Selector('.Table .Button.next');
export const rowsPerPageButton = Selector('.Table .size .Button');
export const addMeasureButton = Selector('.addMeasure button');
export const removeMeasureButton = Selector('.Measure').nth(1).find('.SelectionPreview .Button');
export const heatDropdown = Selector('.Heatmap .Select');
export const sectionToggle = (sectionName) =>
  Selector('.ReportControlPanel .sectionTitle')
    .withText(new RegExp(sectionName, 'i')) // we are using CSS text-transform uppercase, which is handled inconsistently across browsers: https://github.com/DevExpress/testcafe/issues/3335
    .find('.sectionToggle');
export const deselectAllButton = Selector('.Button').withText('Deselect All');
export const addDefinitionButton = Selector('.AddDefinition');
export const definitionEntry = (name) => Selector('.Checklist .label').withText(name);
export const lineButton = Selector('.measureContainer .Button').withText('Line');
export const tableScrollableContainer = reportTable.find('table');
export const objectViewBtn = reportTable.find('.ObjectViewBtn').nth(0);
export const objectVariableModal = Selector('.ObjectVariableModal');
export const renameVariablesBtn = Selector('.actionBar .Button').withText('Rename Variables');
export const newNameInput = (name) => Selector('.Modal tbody tr').withText(name).find('.Input');
export const updateVariableBtn = Selector('.Modal__actions .Button').withText('Update');
export const viewSelect = Selector('.View .activateButton');
export const numberReportInfo = reportRenderer.find('.Number .label');
