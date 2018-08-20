export function isValidJSON(text) {
  try {
    JSON.parse(text);
    return true;
  } catch (e) {
    return false;
  }
}

export function isEmpty(obj) {
  for (var key in obj) {
    if (obj.hasOwnProperty(key)) return false;
  }
  return true;
}

/**
 * Important: this does not check for deep equality
 * @param {*} objA
 * @param {*} objB
 */
export function isEqual(objA, objB) {
  if (objA === objB) {
    return true;
  }

  if (!objA || !objB || typeof objA !== 'object' || typeof objB !== 'object') {
    return objA === objB;
  }

  if (Object.keys(objA).length !== Object.keys(objB).length) {
    return false;
  }

  for (let aKey in objA) {
    if (objA[aKey] !== objB[aKey]) {
      return false;
    }
  }

  return true;
}
