export function isUnique(data, array) {
  return array.filter(entry => entry === data).length === 1;
}
