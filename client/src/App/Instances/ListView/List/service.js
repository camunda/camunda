export const areIdsArray = (selection, total) =>
  Array.isArray(selection.ids) && selection.ids.length === total;

export const areIdsSet = (selection, total) =>
  selection.ids && selection.ids.size === total && selection.ids.size !== 0;

export const getModifiedIdSet = ({isAdded, set, id}) => {
  return (isAdded && set.add(id)) || (set.delete(id) && set);
};

/**
 * Applied when a instance ids filter is active
 * @param {String} of raw ids with spaces etc.
 * @return {Array} of instance ids;
 */
export const createIdArrayFromFilterString = ids =>
  ids.split(/[ ,\t\n]+/).filter(Boolean);
