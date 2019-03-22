export function isPositiveNumber(value) {
  return isFloatNumber(value) && +value > 0;
}

// match float number: https://stackoverflow.com/a/10256077
export function isFloatNumber(value) {
  return /^[+-]?\d+\.?\d*$/.test(value);
}

export function isNonNegativeNumber(value) {
  if (typeof value === 'number') {
    return +value >= 0;
  }
  if (typeof value === 'string') {
    return value.trim() && !isNaN(value.trim()) && +value >= 0;
  }
}
