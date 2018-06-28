import {formatDate} from './date';

describe('date utils', () => {
  describe('formatDate', () => {
    it('should return -- if date is null', () => {
      expect(formatDate(null)).toBe('--');
    });

    it('should return formatted date', () => {
      // given
      const givenDate = new Date('Thu Jun 28 2018 13:59:05');
      const expectedDate = '28 Jun 2018 | 13:59:05';

      // then
      expect(formatDate(givenDate)).toEqual(expectedDate);
    });
  });
});
