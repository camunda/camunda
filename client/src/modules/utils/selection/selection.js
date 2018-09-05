export function getSelectionById(selections, id) {
  const index = selections.findIndex(({selectionId}) => selectionId === id);
  return {...selections[index], index};
}
