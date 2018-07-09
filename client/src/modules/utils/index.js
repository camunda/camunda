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

export function isEqual(objA, objB) {
  return (
    typeof objA === typeof objB && JSON.stringify(objA) === JSON.stringify(objB)
  );
}
