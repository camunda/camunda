import * as numberParser from './NumberParser';

it('should check for valid float number', () => {
  expect(numberParser.isFloatNumber('123')).toBe(true);
  expect(numberParser.isFloatNumber('123.')).toBe(true);
  expect(numberParser.isFloatNumber('123.231')).toBe(true);
  expect(numberParser.isFloatNumber('+123')).toBe(true);
  expect(numberParser.isFloatNumber('-123.123')).toBe(true);
  expect(numberParser.isFloatNumber('123.a')).toBe(false);
  expect(numberParser.isFloatNumber('as.12')).toBe(false);
});

it('should check for valid integer', () => {
  expect(numberParser.isIntegerNumber('123')).toBe(true);
  expect(numberParser.isIntegerNumber('123.12')).toBe(false);
  expect(numberParser.isIntegerNumber('asd')).toBe(false);
  expect(numberParser.isIntegerNumber('+123')).toBe(true);
});

it('should check for positive number', () => {
  expect(numberParser.isPositiveNumber('+123')).toBe(true);
  expect(numberParser.isPositiveNumber('123')).toBe(true);
  expect(numberParser.isPositiveNumber('123.123')).toBe(true);
  expect(numberParser.isPositiveNumber('-123')).toBe(false);
});
