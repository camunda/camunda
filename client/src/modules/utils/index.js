export function isValidJSON(text) {
  try {
    JSON.parse(text);
    return true;
  } catch (e) {
    return false;
  }
}

/**
 * Similar to lodash's compact(array): Removes entries with falsy values from the object
 * i.e. false, null, 0, "", undefined, and NaN
 * @param {object} object: object to make compact
 */
export function compactObject(object) {
  return Object.entries(object).reduce((obj, [key, value]) => {
    return !!value ? {...obj, [key]: value} : obj;
  }, {});
}
