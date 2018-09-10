const areIdsArray = (selection, total) =>
  Array.isArray(selection.ids) && selection.ids.length === total;

const areIdsSet = (selection, total) =>
  selection.ids && selection.ids.size === total && selection.ids.size !== 0;

const getModifiedIdSet = ({isAdded, set, id}) => {
  return (isAdded && set.add(id)) || (set.delete(id) && set);
};

export {areIdsArray, areIdsSet, getModifiedIdSet};
