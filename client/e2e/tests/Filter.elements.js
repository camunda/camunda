/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const typeahead = Selector('.Modal__content .Typeahead');
export const typeaheadInput = Selector('.Modal__content .Typeahead .Input');
export const typeaheadOption = (text) =>
  Selector('.Modal__content .Typeahead .DropdownOption').withText(text);
export const variableFilterOperatorButton = (text) =>
  Selector('.Modal .buttonRow .Button').withText(text);
export const variableFilterValueInput = Selector('.Modal .valueFields input').nth(-1);
export const typeaheadAddButton = Selector('.InputGroup > .Button').withText('Add');
export const dateFilterTypeSelect = Selector('.DateRangeInput .Dropdown');
export const dateFilterTypeOption = (text) =>
  Selector('.DateRangeInput .DropdownOption').withText(text);
export const dateFilterStartInput = Selector('.DateFields .PickerDateInput:first-child input');
export const dateFilterEndInput = Selector('.DateFields .PickerDateInput:last-child input');
export const pickerDate = (number) =>
  Selector('.DateFields .rdrMonths .rdrMonth:first-child .rdrDay').withText(number);
export const infoText = Selector('.Modal__content .tip');
export const dateTypeSelect = Selector('.selectGroup > .Select');
export const unitSelect = Selector('.unitSelection .Select');
export const customDateInput = Selector('.unitSelection').find('input');
export const durationFilterOperator = Selector('.DurationFilter .Select');
export const durationFilterInput = Selector('.DurationFilter input[type="text"]');
export const modalCancel = Selector('.Modal .Button').withText('Cancel');
export const addValueButton = Selector('.Modal .addValueButton');
export const stringValues = Selector('.TypeaheadMultipleSelection__valueList');
export const firstMultiSelectValue = Selector(
  '.TypeaheadMultipleSelection__valueList .LabeledInput'
);
export const multiSelectValue = (text) => firstMultiSelectValue.withText(text);
export const confirmButton = Selector('.Modal .Modal__actions .primary');
