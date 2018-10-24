import {isValidNumber, isChart} from './service';

it('Should return false if the passed string contains letters', () => {
  expect(isValidNumber('123h')).toBe(false);
});

it('Should return false if the passed string is negative number', () => {
  expect(isValidNumber('-1')).toBe(false);
});

it('Should return true if a possitve string number is passed', () => {
  expect(isValidNumber('1')).toBe(true);
});

it('Should return true if a number is passed', () => {
  expect(isValidNumber(1)).toBe(true);
});

describe('isChart', () => {
  it('should return false if the combined report is empty', () => {
    const result = isChart({
      reportType: 'combined',
      data: {
        reportIds: []
      }
    });
    expect(result).toBe(false);
  });

  it('should return true if the combined report is bar or line chart', () => {
    const result = isChart({
      reportType: 'combined',
      data: {
        reportIds: ['test']
      },
      result: {
        test: {
          data: {
            visualization: 'bar'
          }
        }
      }
    });
    expect(result).toBe(true);
  });
});
