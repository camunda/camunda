import {isEqual} from './index';

describe('utils', () => {
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
