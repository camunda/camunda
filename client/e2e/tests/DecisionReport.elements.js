/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const reportRenderer = Selector('.ReportRenderer');
export const decisionTable = Selector('.DecisionTable');
export const decisionTableCell = (row, column) =>
  Selector('.DecisionTable tbody tr')
    .nth(row)
    .find('td')
    .nth(column);
export const primaryModalButton = Selector('.Modal .Modal__actions .Button--primary');
export const filterButton = Selector('.Filter__dropdown .activateButton');
export const filterOption = text => Selector('.Filter .DropdownOption').withText(text);
export const variableFilterTypeahead = Selector('.Modal__content .Typeahead');
export const variableFilterTypeaheadOption = text =>
  Selector('.Modal__content .Typeahead .DropdownOption').withText(text);
export const variableFilterOperatorButton = text =>
  Selector('.Modal .VariableFilter__buttonRow .Button').withText(text);
export const variableFilterValueInput = Selector('.Modal .VariableFilter__valueFields input');
