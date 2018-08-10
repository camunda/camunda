export function getSelectionById(selections, id) {
  return selections
    .map(
      (selection, index) =>
        selection.selectionId === id && {...selection, index}
    )
    .filter(selection => selection.selectionId >= 0)
    .shift();
}
