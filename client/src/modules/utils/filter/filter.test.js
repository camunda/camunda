import {parseFilterForRequest, getFilterQueryString} from './filter';

import {DEFAULT_FILTER} from '../../constants/filter';

describe.only('parseFilterForRequest', () => {
  it('should parse both active and incidents filter selection', () => {
    const filter = {active: true, incidents: true};

    expect(parseFilterForRequest(filter).running).toBe(true);
    expect(parseFilterForRequest(filter).incidents).toBe(true);
    expect(parseFilterForRequest(filter).active).toBe(true);
  });
  it('should parse only active filter selection', () => {
    const filter = {active: true, incidents: false};

    expect(parseFilterForRequest(filter).running).toBe(true);
    expect(parseFilterForRequest(filter).incidents).toBe(false);
    expect(parseFilterForRequest(filter).active).toBe(true);
  });
  it('should parse only incidents filter selection', () => {
    const filter = {active: false, incidents: true};

    expect(parseFilterForRequest(filter).running).toBe(true);
    expect(parseFilterForRequest(filter).incidents).toBe(true);
    expect(parseFilterForRequest(filter).active).toBe(false);
  });
  it('should parse empty filter selection', () => {
    const filter = {active: false, incidents: false};

    expect(parseFilterForRequest(filter).running).toBe(false);
    expect(parseFilterForRequest(filter).incidents).toBe(false);
    expect(parseFilterForRequest(filter).active).toBe(false);
  });
});

describe('getFilterQueryString', () => {
  it('should return a query string', () => {
    const queryString = '?filter={"active":true,"incidents":true}';

    expect(getFilterQueryString(DEFAULT_FILTER)).toBe(queryString);
  });

  it('should support objects with various values', () => {
    const input = {a: true, b: true, c: 'X'};
    const output = '?filter={"a":true,"b":true,"c":"X"}';

    expect(getFilterQueryString(input)).toBe(output);
  });
});
