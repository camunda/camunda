import {fieldParser} from './service';

describe('Filters/service.js', () => {
  describe('fieldParser.ids()', () => {
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
});
