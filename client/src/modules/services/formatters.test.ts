/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  getHighlightedText,
  duration as durationFormatter,
  convertDurationToObject,
  convertToDecimalTimeUnit,
  convertDurationToSingleNumber,
  convertToMilliseconds,
} from './formatters';
const nbsp = '\u00A0';

describe('getHighlightedText', () => {
  it('Should wrap the highlighted text in a span and give it textBold class', () => {
    const results = getHighlightedText('test text', 'text') as JSX.Element[];
    expect(results[1]?.props.children).toBe('text');
    expect(results[1]?.props.className).toBe('textBold');
  });

  it('Should return the same text as string if the highlight is empty', () => {
    const results = getHighlightedText('test text', '');
    expect(results).toBe('test text');
  });

  it('the regex should match only from the start of the text if specified', () => {
    const notMatch = getHighlightedText('test text', 'text', true);
    expect(notMatch.length).toBe(1);
    const results = getHighlightedText('test text', 'test', true) as JSX.Element[];
    expect(results[1]?.props.children).toBe('test');
    expect(results[1]?.props.className).toBe('textBold');
  });

  it('should work with special characters', () => {
    const results = getHighlightedText('test)', ')') as JSX.Element[];
    expect(results[1]?.props.children).toBe(')');
    expect(results[1]?.props.className).toBe('textBold');
  });
});

describe('durationFormatter', () => {
  it('should format ms input into human readable string', () => {
    const time = 27128;

    expect(durationFormatter(time)).toBe(`27s${nbsp}128ms`);
  });

  it('should handle zero well', () => {
    expect(durationFormatter(0)).toBe('0ms');
  });

  it('should single unit well', () => {
    expect(durationFormatter(5 * 60 * 60 * 1000)).toBe('5h');
  });

  it('should handle single millisecond durations', () => {
    expect(durationFormatter(1)).toBe('1ms');
  });

  it('should handle millisecond durations that are below 1', () => {
    expect(durationFormatter({value: 0.2, unit: 'millis'})).toBe('0.2ms');
  });

  it('should truncate to 2 significant figures for milliseconds', () => {
    expect(durationFormatter({value: 0.24324234234, unit: 'millis'})).toBe('0.24ms');
    expect(durationFormatter({value: 1.234343, unit: 'seconds'})).toBe(`1s${nbsp}234.34ms`);
  });

  it('should not floor millisecond durations only', () => {
    expect(durationFormatter({value: 1.3, unit: 'millis'})).toBe('1.3ms');
    expect(durationFormatter({value: 1.2, unit: 'seconds'})).toBe(`1s${nbsp}200ms`);
  });

  it('should handle a time object', () => {
    expect(durationFormatter({value: 14, unit: 'seconds'})).toBe('14s');
  });

  it('should normalize a time object', () => {
    expect(durationFormatter({value: 15, unit: 'days'})).toBe(`2wk${nbsp}1d`);
  });

  it('should use a precision', () => {
    expect(durationFormatter(123456789, 2)).toBe(`1${nbsp}day${nbsp}10${nbsp}hours`);
    expect(durationFormatter(29009802502, 3)).toBe(
      `11${nbsp}months${nbsp}5${nbsp}days${nbsp}18${nbsp}hours`
    );
    expect(durationFormatter(123456789, 4)).toBe(
      `1${nbsp}day${nbsp}10${nbsp}hours${nbsp}17${nbsp}minutes${nbsp}37${nbsp}seconds`
    );
  });

  it('should use default precision of 3 when the precision is turned off in the report', () => {
    expect(durationFormatter(29009802502, null)).toBe(
      `11${nbsp}months${nbsp}5${nbsp}days${nbsp}18${nbsp}hours`
    );
  });

  it('should return -- for nondefined values', () => {
    expect(durationFormatter()).toBe('--');
    expect(durationFormatter('')).toBe('--');
    expect(durationFormatter(null)).toBe('--');
  });

  it('should return value in short notation', () => {
    expect(durationFormatter(123456789, undefined, true)).toBe(
      `1d${nbsp}10h${nbsp}17min${nbsp}36s${nbsp}789ms`
    );
    expect(durationFormatter(123456789, null, true)).toBe(`1d${nbsp}10h${nbsp}18min`);
  });
});

describe('convertDurationToObject', () => {
  it('should return an object with value and unit', () => {
    const result = convertDurationToObject(123);

    expect(result.unit).toBeDefined();
    expect(result.value).toBeDefined();
  });

  it('should convert a millisecond value', () => {
    expect(convertDurationToObject(123)).toEqual({value: '123', unit: 'millis'});
    expect(convertDurationToObject(4 * 60 * 1000)).toEqual({value: '4', unit: 'minutes'});
    expect(convertDurationToObject(1000)).toEqual({value: '1', unit: 'seconds'});
    expect(convertDurationToObject(1001)).toEqual({value: '1001', unit: 'millis'});
  });
});

describe('convertToDecimalTimeUnit', () => {
  expect(convertToDecimalTimeUnit(123)).toEqual({value: '123', unit: 'millis'});
  expect(convertToDecimalTimeUnit(4 * 60 * 1000)).toEqual({value: '4', unit: 'minutes'});
  expect(convertToDecimalTimeUnit(1000)).toEqual({value: '1', unit: 'seconds'});
  expect(convertToDecimalTimeUnit(1001)).toEqual({value: '1.001', unit: 'seconds'});
});

describe('convertDurationToSingleNumber', () => {
  it('should return simple numbers unprocessed', () => {
    expect(convertDurationToSingleNumber(123)).toBe(123);
  });

  it('should convert duration objects to millis', () => {
    expect(convertDurationToSingleNumber({value: '123', unit: 'millis'})).toBe(123);
    expect(convertDurationToSingleNumber({value: '2', unit: 'minutes'})).toBe(2 * 60 * 1000);
    expect(convertDurationToSingleNumber({value: '1.5', unit: 'seconds'})).toBe(1500);
  });
});

describe('convertToMilliseconds', () => {
  expect(convertToMilliseconds(5, 'seconds')).toBe(5000);
  expect(convertToMilliseconds(2, 'months')).toBe(5184000000);
  expect(convertToMilliseconds(3, 'hours')).toBe(10800000);
  expect(convertToMilliseconds(100, 'millis')).toBe(100);
});
