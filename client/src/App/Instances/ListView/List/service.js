/**
 * Applied when a instance ids filter is active
 * @param {String} of raw ids with spaces etc.
 * @return {Array} of instance ids;
 */
export const createIdArrayFromFilterString = ids =>
  ids.split(/[ ,\t\n]+/).filter(Boolean);
