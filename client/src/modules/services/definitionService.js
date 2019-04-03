/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function extractDefinitionName(key, xml) {
  return (
    new DOMParser()
      .parseFromString(xml, 'text/xml')
      .querySelector(`[id="${key}"]`)
      .getAttribute('name') || key
  );
}
