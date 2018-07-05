import {parseQueryString, isEqual} from './service';

describe('Instances service', () => {
  describe('parseQueryString', () => {
    it('should return a empty object for invalid querys string', () => {
      const invalidInputA = '?filter={"active":truef,"incidents":true}';
      const invalidInputB = '?filter=';
      const invalidInputC = '';

      expect(parseQueryString(invalidInputA)).toEqual({});
      expect(parseQueryString(invalidInputB)).toEqual({});
      expect(parseQueryString(invalidInputC)).toEqual({});
    });

    it('should return an object for valid query strings', () => {
      const input = '?filter={"a":true,"b":true,"c":"X"}';
      const output = {filter: {a: true, b: true, c: 'X'}};

      expect(parseQueryString(input)).toEqual(output);
    });

    it('should support query strings with more params', () => {
      const input = '?filter={"a":true,"b":true,"c":"X"}&extra={"extra": true}';
      const output = {
        filter: {a: true, b: true, c: 'X'},
        extra: {extra: true}
      };

      expect(parseQueryString(input)).toEqual(output);
    });
  });

  describe('isEqual', () => {
    it('should return true when objects are equal', () => {
      expect(isEqual(null, null)).toBe(true);
      expect(isEqual({x: {y: {a: 2}}}, {x: {y: {a: 2}}})).toBe(true);
    });

    it('should return true when objects are equal', () => {
      expect(isEqual(null, 10)).toBe(false);
      expect(isEqual({x: {y: {a: 2}}}, {x: {y: {a: 'hello'}}})).toBe(false);
      expect(isEqual({x: {y: {a: 2}}}, '{x: {y: {a: 2}}}')).toBe(false);
    });
  });
});
