import {convertDurationToObject, convertDurationToSingleNumber} from './service';

jest.mock('request', () => {
  return {
    get: jest.fn(),
    post: jest.fn(),
    del: jest.fn(),
    put: jest.fn()
  };
});

describe('convertDurationToObject', () => {
  it('should return an object with value and unit', () => {
    const result = convertDurationToObject(123);

    expect(result.unit).toBeDefined();
    expect(result.value).toBeDefined();
  });

  it('should convert a millisecond value', () => {
    expect(convertDurationToObject(123)).toEqual({value: '123', unit: 'milliseconds'});
    expect(convertDurationToObject(4 * 60 * 1000)).toEqual({value: '4', unit: 'minutes'});
    expect(convertDurationToObject(1000)).toEqual({value: '1', unit: 'seconds'});
    expect(convertDurationToObject(1001)).toEqual({value: '1001', unit: 'milliseconds'});
  });
});

describe('convertDurationToSingleNumber', () => {
  it('should return simple numbers unprocessed', () => {
    expect(convertDurationToSingleNumber(123)).toBe(123);
  });

  it('should convert duration objects to milliseconds', () => {
    expect(convertDurationToSingleNumber({value: '123', unit: 'milliseconds'})).toBe(123);
    expect(convertDurationToSingleNumber({value: '2', unit: 'minutes'})).toBe(2 * 60 * 1000);
    expect(convertDurationToSingleNumber({value: '1.5', unit: 'seconds'})).toBe(1500);
  });
});
