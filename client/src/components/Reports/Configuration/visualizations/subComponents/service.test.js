import {isValidNumber} from './service';

it('should return false if the passed string contains letters', () => {
  expect(isValidNumber('123h')).toBe(false);
});

it('should return false if the passed string is negative number', () => {
  expect(isValidNumber('-1')).toBe(false);
});

it('should return true if a possitve string number is passed', () => {
  expect(isValidNumber('1')).toBe(true);
});

it('should return true if a number is passed', () => {
  expect(isValidNumber(1)).toBe(true);
});
