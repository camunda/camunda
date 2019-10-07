/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {trimValue} from 'modules/utils';

export function createBeautyfiedJSON(validJSONstring, indentationSpace = 0) {
  return JSON.stringify(removeTabs(validJSONstring), null, indentationSpace);
}

export function removeTabs(validJSONstring) {
  // removes all possible spaces, a user could have added during in-line edit.
  return JSON.parse(validJSONstring);
}

export function removeWhiteSpaces(value) {
  return value.replace(/\s/g, '');
}

export function removeLineBreaks(value) {
  return value.replace(/\r?\n|\r/g, '');
}

export function trimVariable(variable) {
  return {
    name: trimValue(variable.name),
    value: trimValue(variable.value)
  };
}
