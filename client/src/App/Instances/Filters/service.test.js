import {fieldParser} from './service';

describe('Filters/service.js', () => {
  describe('fieldParser.errorMessage()', () => {
    it('should return the value for errorMessage', () => {
      const input = 'lorem ipsum';
      const output = fieldParser.errorMessage(input);

      expect(output).toEqual(input);
    });
    it('should return null for empty value', () => {
      const input = '';
      const output = fieldParser.errorMessage(input);

      expect(output).toEqual(null);
    });
  });

  describe('fieldParser.ids()', () => {
    it('should return an array for ids', () => {
      const input = '1 2 3';
      const output = fieldParser.ids(input);

      expect(output).toEqual(['1', '2', '3']);
    });

    it('should separate the values by string and comma', () => {
      const input = ' 1, 2 , 3,';
      const output = fieldParser.ids(input);

      expect(output).toEqual(['1', '2', '3']);
    });
  });

  describe('fieldParser.ids()', () => {
    it('should return an array for ids', () => {
      const input = '1 2 3';
      const output = fieldParser.ids(input);

      expect(output).toEqual(['1', '2', '3']);
    });

    it('should separate the values by string and comma', () => {
      const input = ' 1, 2 , 3,';
      const output = fieldParser.ids(input);

      expect(output).toEqual(['1', '2', '3']);
    });
  });

  describe('fieldParser.startDate()', () => {
    it('should return object with two keys', () => {
      const input = 'July 05 2018';
      const output = fieldParser.startDate(input);

      expect(output.startDateAfter).toBeDefined();
      expect(output.startDateBefore).toBeDefined();
      expect(output.startDateAfter.length).toBe(28);
      expect(output.startDateBefore.length).toBe(28);
    });

    it('should return the same date for startDateAfter', () => {
      const input = 'July 05 2018';
      const output = fieldParser.startDate(input);

      expect(output.startDateAfter).toBe('2018-07-05T00:00:00.000+0200');
    });
    it('should return startDateBefore = date + 1 day, when no time is provided', () => {
      const input = 'July 05 2018';
      const output = fieldParser.startDate(input);

      expect(output.startDateBefore).toBe('2018-07-06T00:00:00.000+0200');
    });
    it('should return startDateBefore = date + 1 minute, when time is provided', () => {
      const input = 'July 05 2018 15:18';
      const output = fieldParser.startDate(input);

      expect(output.startDateBefore).toBe('2018-07-05T15:19:00.000+0200');
    });
  });
});
