import {range} from './range';

export function formatDate(date) {
  if (!(date instanceof Date)) {
    throw new Error(`Expected ${date} to be instance of Date`);
  }

  const year = date.getFullYear();
  const month = date.getMonth() + 1;
  const day = date.getDate();

  return `${zeroCapNumber(4, year)}-${zeroCapNumber(2, month)}-${zeroCapNumber(2, day)}`;
}

export function zeroCapNumber(digits, number) {
  if (number < 1) {
    return number; // does not work for numbers smaller than 1
  }

  const numberDigits = Math.floor(Math.log10(number)) + 1;
  const diff = digits - numberDigits;

  if (diff < 1) {
    return number; // no need for adjustment
  }

  return range(1, diff).reduce(strNumber => '0' + strNumber, number);
}
