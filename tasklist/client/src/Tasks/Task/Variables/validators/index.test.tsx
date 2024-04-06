/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {
  validateNameCharacters,
  validateNameComplete,
  validateDuplicateNames,
  validateValueComplete,
  validateValueJSON,
} from './index';

const mockMeta = {
  blur: vi.fn(),
  change: vi.fn(),
  focus: vi.fn(),
};

describe('Validators', () => {
  beforeEach(() => {
    vi.useFakeTimers({
      shouldAdvanceTime: true,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('validateNameCharacters', () => {
    it.each(['abc', '123'])('should allow: %s', (variableName) => {
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

    it.each([
      '"',
      ' ',
      'test ',
      '"test"',
      'test\twith\ttab',
      'line\nbreak',
      'carriage\rreturn',
      'form\ffeed',
    ])(`should not allow: %s`, (variableName) => {
      const result = validateNameCharacters(
        variableName,
        {newVariables: [{name: variableName}]},
        {
          ...mockMeta,
          name: 'newVariables[0].name',
        },
      );

      vi.runOnlyPendingTimers();

      expect(result).toBe('Name is invalid');
    });
  });

  describe('validateNameComplete', () => {
    it.each(['abc', 'true', '123'])(`should allow: %s`, (variableName) => {
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

    it.each(['', ' ', '           '])(
      'should not allow empty spaces',
      (variableName) => {
        const result = validateNameComplete(
          variableName,
          {newVariables: [{name: variableName, value: '1'}]},
          {
            ...mockMeta,
            name: 'newVariables[0].name',
          },
        );

        vi.runOnlyPendingTimers();

        expect(result).resolves.toBe('Name has to be filled');
      },
    );
  });

  describe('validateDuplicateNames', () => {
    it('should allow', () => {
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

    it.each(['test1', 'test2'])('should not allow: %s', (variableName) => {
      const [activeResult, errorResult, validatingResult] = [
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
      ];

      vi.runOnlyPendingTimers();

      expect(activeResult).resolves.toBe('Name must be unique');
      expect(errorResult).toBe('Name must be unique');
      expect(validatingResult).resolves.toBe('Name must be unique');
    });
  });

  describe('validateValueComplete', () => {
    it.each(['"abc"', '123', 'true', '{"name": "value"}', '[1, 2, 3]'])(
      'should allow: %s',
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

    it('should allow an empty value', () => {
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

    it.each(['abc', '"abc', '{name: "value"}', '[[0]', '() => {}'])(
      'should not allow: %s',
      (value) => {
        const result = validateValueComplete(
          value,
          {
            newVariables: [{name: 'test', value}],
          },
          {
            ...mockMeta,
            name: `newVariables[0].value`,
          },
        );

        vi.runOnlyPendingTimers();

        expect(result).resolves.toBe('Value has to be JSON or a literal');
      },
    );
  });

  describe('validateValueJSON', () => {
    it.each(['"abc"', '123', 'true', '{"name": "value"}', '[1, 2, 3]'])(
      'should allow: %s',
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

    it.each(['abc', '"abc', '{name: "value"}', '[[0]', '() => {}'])(
      'should not allow: %s',
      (value) => {
        const result = validateValueJSON(
          value,
          {
            newVariables: [{name: 'test', value}],
          },
          {
            ...mockMeta,
            name: 'newVariables[0].value',
          },
        );

        vi.runOnlyPendingTimers();

        expect(result).resolves.toBe('Value has to be JSON or a literal');
      },
    );
  });
});
