export function isValidNumber(value) {
  return isIntegerNumber(value) || isFloatNumber(value);
}

export function isPositiveNumber(value) {
  return isValidNumber(value) && +value > 0;
}

// match integer: https://stackoverflow.com/a/1779019
export function isIntegerNumber(value) {
  return /^[+-]?\d+?$/.test(value);
}

// match float number: https://stackoverflow.com/a/10256077
export function isFloatNumber(value) {
  return /^[+-]?\d+(\.\d+)?$/.test(value);
}
