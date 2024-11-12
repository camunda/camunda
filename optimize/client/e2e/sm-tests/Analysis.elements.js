/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Selector} from 'testcafe';

export const flowNode = (id) => Selector(`[data-element-id="${id}"]`);
export const statisticsDiagram = Selector('.Statistics canvas');
export const endEventOverlay = Selector('.DiagramBehavior__end-event-statistics');
export const gatewayCancelButton = Selector('[name="gateway"] button');
export const gatewayInput = Selector('[name="gateway"] .SelectionPreview');
export const endEventInput = Selector('[name="endEvent"] .SelectionPreview');
export const branchAnalysisLink = Selector('.cds--tab--list a').withText('Branch analysis');
export const heatmapEl = Selector('svg .viewport image');
export const chart = Selector('.DurationChart canvas');
export const variablesTable = Selector('.VariablesTable.Table');
export const variablesTableRow = (text) => variablesTable.find('tr').withText(text);
export const outliersTable = Selector('.OutlierDetailsTable');
export const outliersTableRow = (text) => outliersTable.find('tr').withText(text);
export const outliersTableDetailsButton = (text) =>
  outliersTableRow(text).find('button').withText('View details');
export const filtersDropdown = Selector('.filterHeader .MenuDropdown');
export const warningMessage = Selector('.cds--inline-notification').withText(
  'No data shown due to incompatible filters'
);
