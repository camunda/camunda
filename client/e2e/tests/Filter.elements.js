/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const variableFilterTypeahead = Selector('.Modal__content .Typeahead');
export const variableFilterTypeaheadInput = Selector('.Modal__content .Typeahead .Input');
export const variableFilterTypeaheadOption = text =>
  Selector('.Modal__content .Typeahead .DropdownOption').withText(text);
export const variableFilterOperatorButton = text =>
  Selector('.Modal .VariableFilter__buttonRow .Button').withText(text);
export const variableFilterValueInput = Selector('.Modal .VariableFilter__valueFields input').nth(
  -1
);
export const dateFilterStartInput = Selector('.DateFields .DateInput:first-child input');
export const dateFilterEndInput = Selector('.DateFields .DateInput:last-child input');
export const pickerDate = number =>
  Selector('.DateFields .rdr-Calendar:first-child .rdr-Day').withText(number);
export const infoText = Selector('.Modal__content .tip');
export const dateTypeSelect = Selector('.selectGroup > .Select');
export const unitSelect = Selector('.unitSelection .Select:first-child');
export const customDateInput = Selector('.unitSelection').find('input');
export const customDateDropdown = Selector('.unitSelection .Select:nth-of-type(2)');
export const durationFilterOperator = Selector('.DurationFilter .Select');
export const durationFilterInput = Selector('.DurationFilter input[type="text"]');
export const modalCancel = Selector('.Modal .Button').withText('Cancel');
export const addValueButton = Selector('.Modal .NumberInput__addValueButton');
export const nullSwitch = Selector('.Modal__content .Form .Switch');
export const stringValues = Selector('.TypeaheadMultipleSelection__valueList');
