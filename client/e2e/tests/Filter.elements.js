/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const variableFilterTypeahead = Selector('.Modal__content .Typeahead');
export const variableFilterTypeaheadOption = text =>
  Selector('.Modal__content .Typeahead .DropdownOption').withText(text);
export const variableFilterOperatorButton = text =>
  Selector('.Modal .VariableFilter__buttonRow .Button').withText(text);
export const variableFilterValueInput = Selector('.Modal .VariableFilter__valueFields input');
export const dateFilterStartInput = Selector('.DateFields input:first-child');
export const pickerDate = number =>
  Selector('.DateFields .rdr-Calendar:first-child .rdr-Day').withText(number);
export const yearFilterButton = Selector('.Button').withText('This Year');

export const relativeDateButton = Selector('.ButtonGroup .Button').withText('Relative Date');
const relativeDateInputs = Selector('.label')
  .withText('In the last')
  .nextSibling();
export const relativeDateInput = relativeDateInputs.find('input');
export const relativeDateDropdown = relativeDateInputs.find('.Select');

export const durationFilterOperator = Selector('.DurationFilter__inputs .Select');
export const durationFilterInput = Selector('.DurationFilter__inputs input[type="text"]');
export const modalCancel = Selector('.Modal .Button').withText('Cancel');
