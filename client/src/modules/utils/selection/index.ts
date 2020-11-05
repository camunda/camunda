/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function getSelectionById(selections: any, id: any) {
  const index = selections.findIndex(
    ({selectionId}: any) => selectionId === id
  );
  return {...selections[index], index};
}

export function serializeInstancesMaps(selections: any) {
  return selections.map((selection: any) => {
    const newSelection = {...selection};
    newSelection.instancesMap = JSON.stringify([...newSelection.instancesMap]);
    return newSelection;
  });
}

export function deserializeInstancesMaps(selections: any) {
  selections &&
    selections.forEach((selection: any) => {
      if (selection.instancesMap) {
        selection.instancesMap = new Map(JSON.parse(selection.instancesMap));
      }
    });
  return selections;
}
