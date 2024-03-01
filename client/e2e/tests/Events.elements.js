/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Selector} from 'testcafe';

export const navItem = Selector('header a[href="#/events/processes/"]');
export const createDropdown = Selector('.Events .createNewProcess button');
export const invoiceEventProcess = Selector('.ListItem').withText('Event Invoice process');
export const editAccess = Selector('.CarbonListItemSingleAction').withText('Edit access');
export const fileInput = Selector('input').withAttribute('type', 'file');
export const firstEl = Selector('.djs-hit');
export const activityTask = Selector('.bpmn-icon-task');
export const saveButton = Selector('.save-button');
export const processName = Selector('.ProcessView .name');
export const addSource = Selector('button.addProcess');
export const processTypeahead = Selector('.DefinitionSelection input[type=text]');
export const variableTypeahead = Selector('.variablesSelector');
export const externalEvents = Selector('.cds--tabs button').withText('External events');
export const startNode = Selector('g[data-element-id=StartEvent_1]');
export const startEvent = Selector('.StartEvent_1 .cds--table-column-checkbox label');
export const activity = Selector('g[data-element-id=Activity_1s5va7f]');
export const bankStart = Selector('.BankTransferStart .cds--table-column-checkbox label');
export const bankEnd = Selector('.BankTransferEnd .cds--table-column-checkbox label');
export const endNode = Selector('g[data-element-id=Event_0m3kxux]');
export const endEvent = Selector('.invoiceProcessed .cds--table-column-checkbox label');
export const eventsTable = Selector('.EventTable');
export const publishButton = Selector('.publish-button');
export const permissionButton = Selector('.permission button');
export const addEventSourceBtn = Selector('.GenerationModal button').withText('Add event source');
export const zoomButton = Selector('.zoomIn');
export const diagram = Selector('.ProcessView .BPMNDiagram');
export const externalEventsTab = Selector('.tabList a').withText('External events');
export const batchDeleteButton = Selector('.cds--action-list button').withText('Delete');
export const eventCheckbox = (index) =>
  Selector('.Table tbody tr').nth(index).find('.cds--table-column-checkbox label');
export const externalEventgroup = (index) =>
  Selector('.ExternalSourceSelection tr .cds--table-column-checkbox').nth(index);
export const sourceMenuButton = (sourceName) =>
  Selector('.SourceMenuButton button').withText(sourceName);
export const editingNotification = Selector('.editingWarning').withText(
  'Changing the process definition may add or remove events.'
);
