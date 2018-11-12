export function getSelectionById(selections, id) {
  const index = selections.findIndex(({selectionId}) => selectionId === id);
  return {...selections[index], index};
}

export function serializeInstancesMaps(selections) {
  return selections.map(selection => {
    const newSelection = {...selection};
    newSelection.instancesMap = JSON.stringify([...newSelection.instancesMap]);
    return newSelection;
  });
}

export function deserializeInstancesMaps(selections) {
  selections &&
    selections.forEach(selection => {
      if (selection.instancesMap) {
        selection.instancesMap = new Map(JSON.parse(selection.instancesMap));
      }
    });
  return selections;
}
