import {isEqual} from './index';

describe('utils', () => {
  describe('isEqual', () => {
    it('should return true when objects are equal', () => {
      expect(isEqual(null, null)).toBe(true);
      expect(isEqual({a: 2, b: 3}, {b: 3, a: 2})).toBe(true);
    });

    it('should return false when objects are not equal', () => {
      expect(isEqual(null, 10)).toBe(false);
      expect(isEqual({a: 2}, {a: 3})).toBe(false);
    });
  });
});
