/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function createBeautyfiedJSON(
  validJSONstring: any,
  indentationSpace = 0
) {
  return JSON.stringify(removeTabs(validJSONstring), null, indentationSpace);
}

export function removeTabs(validJSONstring: any) {
  // removes all possible spaces, a user could have added during in-line edit.
  return JSON.parse(validJSONstring);
}

export function removeWhiteSpaces(value: any) {
  return value.replace(/\s/g, '');
}

export function removeLineBreaks(value: any) {
  return value.replace(/\r?\n|\r/g, '');
}
