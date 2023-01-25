/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  validateNameCharacters,
  validateNameComplete,
  validateDuplicateNames,
  validateValueComplete,
  validateValueJSON,
} from './index';

const mockMeta = {
  blur: jest.fn(),
  change: jest.fn(),
  focus: jest.fn(),
};

describe('Validators', () => {
  describe('validateNameCharacters', () => {
    it('should validate', () => {
      ['abc', '123'].forEach((variableName) => {
        expect(
          validateNameCharacters(
            variableName,
            {newVariables: [{name: variableName}]},
            {
              ...mockMeta,
              name: 'newVariables[0].name',
            },
          ),
        ).toBeUndefined();
      });
    });

    it('should not validate', () => {
      [
        '"',
        ' ',
        'test ',
        '"test"',
        'test\twith\ttab',
        'line\nbreak',
        'carriage\rreturn',
        'form\ffeed',
      ].forEach((variableName) => {
        expect(
          validateNameCharacters(
            variableName,
            {newVariables: [{name: variableName}]},
            {
              ...mockMeta,
              name: 'newVariables[0].name',
            },
          ),
        ).toBe('Name is invalid');
      });
    });
  });

  describe('validateNameComplete', () => {
    it('should validate', () => {
      ['abc', 'true', '123'].forEach((variableName) => {
        expect(
          validateNameComplete(
            variableName,
            {newVariables: [{name: variableName, value: '1'}]},
            {
              ...mockMeta,
              name: 'newVariables[0].name',
            },
          ),
        ).toBeUndefined();
      });
    });

    it('should not validate', () => {
      ['', ' ', '           '].forEach((variableName) => {
        expect(
          validateNameComplete(
            variableName,
            {newVariables: [{name: variableName, value: '1'}]},
            {
              ...mockMeta,
              name: 'newVariables[0].name',
            },
          ),
        ).resolves.toBe('Name has to be filled');
      });
    });
  });

  describe('validateDuplicateNames', () => {
    it('should validate', () => {
      expect(
        validateDuplicateNames(
          'test3',
          {
            '#test1': 'value1',
            newVariables: [
              {name: 'test2', value: 'value2'},
              {name: 'test3', value: 'value3'},
            ],
          },
          {
            ...mockMeta,
            name: '',
          },
        ),
      ).toBe(undefined);

      expect(
        validateDuplicateNames(
          'test2',
          {
            '#test1': 'value1',
            newVariables: [
              {name: 'test2', value: 'value2'},
              {name: 'test2', value: 'value3'},
            ],
          },
          {
            ...mockMeta,
            name: '',
          },
        ),
      ).toBe(undefined);
    });

    it('should not validate', () => {
      ['test1', 'test2'].forEach((variableName) => {
        expect(
          validateDuplicateNames(
            variableName,
            {
              '#test1': 'value1',
              newVariables: [
                {name: 'test2', value: 'value2'},
                {name: 'test2', value: 'value3'},
              ],
            },
            {
              ...mockMeta,
              name: '',
              active: true,
            },
          ),
        ).resolves.toBe('Name must be unique');
      });

      ['test1', 'test2'].forEach((variableName) => {
        expect(
          validateDuplicateNames(
            variableName,
            {
              '#test1': 'value1',
              newVariables: [
                {name: 'test2', value: 'value2'},
                {name: 'test2', value: 'value3'},
              ],
            },
            {
              ...mockMeta,
              name: '',
              error: 'Name must be unique',
            },
          ),
        ).toBe('Name must be unique');
      });

      ['test1', 'test2'].forEach((variableName) => {
        expect(
          validateDuplicateNames(
            variableName,
            {
              '#test1': 'value1',
              newVariables: [
                {name: 'test2', value: 'value2'},
                {name: 'test2', value: 'value3'},
              ],
            },
            {
              ...mockMeta,
              name: '',
              validating: true,
            },
          ),
        ).resolves.toBe('Name must be unique');
      });
    });
  });

  describe('validateValueComplete', () => {
    it('should validate', () => {
      ['"abc"', '123', 'true', '{"name": "value"}', '[1, 2, 3]'].forEach(
        (value) => {
          expect(
            validateValueComplete(
              value,
              {
                newVariables: [{name: 'test', value}],
              },
              {
                ...mockMeta,
                name: `newVariables[0].value`,
              },
            ),
          ).toBeUndefined();
        },
      );

      expect(
        validateValueComplete(
          '',
          {
            newVariables: [{name: '', value: ''}],
          },
          {
            ...mockMeta,
            name: `newVariables[0].value`,
          },
        ),
      ).toBeUndefined();
    });

    it('should not validate', () => {
      ['abc', '"abc', '{name: "value"}', '[[0]', '() => {}'].forEach(
        (value) => {
          expect(
            validateValueComplete(
              value,
              {
                newVariables: [{name: 'test', value}],
              },
              {
                ...mockMeta,
                name: `newVariables[0].value`,
              },
            ),
          ).resolves.toBe('Value has to be JSON or a literal');
        },
      );
    });
  });

  describe('validateValueJSON', () => {
    it('should validate', () => {
      ['"abc"', '123', 'true', '{"name": "value"}', '[1, 2, 3]'].forEach(
        (value) => {
          expect(
            validateValueJSON(
              value,
              {
                newVariables: [{name: 'test', value}],
              },
              {
                ...mockMeta,
                name: 'newVariables[0].value',
              },
            ),
          ).toBeUndefined();
        },
      );
    });

    it('should not validate', () => {
      ['abc', '"abc', '{name: "value"}', '[[0]', '() => {}'].forEach(
        (value) => {
          expect(
            validateValueJSON(
              value,
              {
                newVariables: [{name: 'test', value}],
              },
              {
                ...mockMeta,
                name: 'newVariables[0].value',
              },
            ),
          ).resolves.toBe('Value has to be JSON or a literal');
        },
      );
    });
  });
});
