/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Selector} from 'testcafe';

export const multiSelect = Selector('.Modal.is-visible .cds--multi-select');
export const multiSelectClearBtn = multiSelect.find('.cds--tag__close-icon');
export const multiSelectOption = (text) =>
  multiSelect.find('.cds--list-box__menu-item').withText(text);
export const multiSelectOptionNumber = (idx) => multiSelect.find('.DropdownOption').nth(idx);
export const variableFilterOperatorButton = (text) =>
  Selector('.Modal .buttonRow .cds--radio-button-wrapper').withText(text);
export const variableTypeahead = Selector('.variableContainer:last-of-type .cds--combo-box input');
export const variableFilterValueInput = Selector('.Modal .ValueListInput input').nth(0);
export const variableOrButton = Selector('.MultipleVariableFilterModal .orButton');
export const removeVariableBtn = Selector('.MultipleVariableFilterModal .removeButton');
export const variableHeader = (text) => Selector('.variableContainer .sectionTitle').withText(text);
export const dateFilterTypeSelect = Selector('.DateRangeInput .Select').nth(0);
export const dateFilterStartInput = Selector('.DateFields .PickerDateInput:first-child input');
export const dateFilterEndInput = Selector('.DateFields .PickerDateInput:last-child input');
export const pickerDate = (number) =>
  Selector('.DateFields .rdrMonths .rdrMonth:first-child .rdrDay:not(.rdrDayPassive)').withText(
    number
  );
export const infoText = Selector('.Modal .tip');
export const dateTypeSelect = Selector('.selectGroup > .Select');
export const unitSelect = Selector('.unitSelection .Select');
export const customDateInput = Selector('.unitSelection').find('input');
export const durationFilterOperator = Selector('.DurationFilter .Select');
export const durationFilterInput = Selector('.DurationFilter input[type="text"]');
export const modalCancel = Selector('.Modal .cancel');
export const stringValues = Selector('.Modal .Checklist tbody');
export const firstMultiSelectValue = Selector('.Modal .Checklist tbody tr');
export const multiSelectValue = (text) => firstMultiSelectValue.withText(text);
export const customValueCheckbox = Selector('.Modal .customValueCheckbox');
export const addValueButton = Selector('.Modal .customValueButton');
export const customValueInput = Selector('.Modal .customValueInput input');
export const addValueToListButton = Selector('.Modal .customValueInput button');
export const editButton = Selector('.ActionItem .buttons button').nth(0);
