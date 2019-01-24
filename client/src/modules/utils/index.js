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

/**
 * @returns a filtered object containing only entries of the provided keys
 * @param {*} object
 * @param any[] keys
 */
export function pickFromObject(object, keys) {
  return Object.entries(object).reduce((result, [key, value]) => {
    return !keys.includes(key) ? result : {...result, [key]: value};
  }, {});
}
