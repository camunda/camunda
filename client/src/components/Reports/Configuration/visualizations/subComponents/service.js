export function isValidNumber(value) {
  if (typeof value === 'number') {
    return true;
  }
  if (typeof value === 'string') {
    return value.trim() && !isNaN(value.trim()) && +value >= 0;
  }
}
