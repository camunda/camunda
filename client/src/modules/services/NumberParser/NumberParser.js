export function isPositiveNumber(value) {
  return isFloatNumber(value) && +value > 0;
}

// match integer: https://stackoverflow.com/a/1779019
export function isIntegerNumber(value) {
  return /^[+-]?\d+?$/.test(value);
}

// match float number: https://stackoverflow.com/a/10256077
export function isFloatNumber(value) {
  return /^[+-]?\d+\.?\d*$/.test(value);
}
