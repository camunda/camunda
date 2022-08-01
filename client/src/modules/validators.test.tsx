/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  validateIdsCharacters,
  validatesIdsComplete,
  validateDateCharacters,
  validateDateComplete,
  validateOperationIdCharacters,
  validateOperationIdComplete,
  validateVariableNameComplete,
  validateVariableValueComplete,
  validateIdsLength,
  validateParentInstanceIdCharacters,
  validateParentInstanceIdNotTooLong,
  validateParentInstanceIdComplete,
  validateVariableNameCharacters,
  validateDecisionIdsCharacters,
  validateDecisionIdsLength,
  validatesDecisionIdsComplete,
} from './validators';

describe('validators', () => {
  let setTimeoutSpy: jest.SpyInstance;

  beforeEach(() => {
    jest.useFakeTimers();
    setTimeoutSpy = jest.spyOn(window, 'setTimeout');
  });
  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should validate ids without delay', async () => {
    expect(validateIdsCharacters('', {})).toBeUndefined();

    expect(validateIdsCharacters('2251799813685543', {})).toBeUndefined();
    expect(validateIdsCharacters('22517998136855430', {})).toBeUndefined();
    expect(validateIdsCharacters('225179981368554300', {})).toBeUndefined();
    expect(validateIdsCharacters('2251799813685543000', {})).toBeUndefined();

    expect(validateIdsCharacters('2251799813685543a', {})).toBe(
      'Key has to be a 16 to 19 digit number, separated by space or comma'
    );
    expect(validateIdsCharacters('a', {})).toBe(
      'Key has to be a 16 to 19 digit number, separated by space or comma'
    );

    expect(validateIdsCharacters('-', {})).toBe(
      'Key has to be a 16 to 19 digit number, separated by space or comma'
    );

    expect(
      validateIdsCharacters('2251799813685543 2251799813685543', {})
    ).toBeUndefined();

    expect(
      validateIdsCharacters('2251799813685543,2251799813685543', {})
    ).toBeUndefined();

    expect(
      validateIdsCharacters('2251799813685543, 2251799813685543', {})
    ).toBeUndefined();

    expect(
      validateIdsCharacters(
        '2251799813685543 22517998136855430 225179981368554300 2251799813685543000',
        {}
      )
    ).toBeUndefined();

    expect(
      validateIdsCharacters('2251799813685543 a 2251799813685543 ', {})
    ).toBe(
      'Key has to be a 16 to 19 digit number, separated by space or comma'
    );

    expect(
      validateIdsCharacters('225179a9813685543 2251799813685543 ', {})
    ).toBe(
      'Key has to be a 16 to 19 digit number, separated by space or comma'
    );

    expect(
      validateIdsCharacters('225179$9813685543 2251799813685543 ', {})
    ).toBe(
      'Key has to be a 16 to 19 digit number, separated by space or comma'
    );

    expect(
      validateIdsLength(
        '2251799813685543 2251799813685543 11111111111111111111',
        {}
      )
    ).toBe(
      'Key has to be a 16 to 19 digit number, separated by space or comma'
    );

    expect(
      validateIdsLength(
        '2251799813685543, 2251799813685543, 11111111111111111111',
        {}
      )
    ).toBe(
      'Key has to be a 16 to 19 digit number, separated by space or comma'
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate ids with delay', async () => {
    expect(
      validatesIdsComplete(
        '2251799813685543 22517998136855430 225179981368554300 2251799813685543000 22517998136855430000 22517998136855430000',
        {}
      )
    ).resolves.toBe(
      'Key has to be a 16 to 19 digit number, separated by space or comma'
    );

    expect(validatesIdsComplete('1', {})).resolves.toBe(
      'Key has to be a 16 to 19 digit number, separated by space or comma'
    );

    expect(validatesIdsComplete('1 1 1 ', {})).resolves.toBe(
      'Key has to be a 16 to 19 digit number, separated by space or comma'
    );

    expect(validatesIdsComplete('1', {})).resolves.toBe(
      'Key has to be a 16 to 19 digit number, separated by space or comma'
    );

    expect(validatesIdsComplete('225179981368554', {})).resolves.toBe(
      'Key has to be a 16 to 19 digit number, separated by space or comma'
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(5);
  });

  it('should validate parent instance id without delay', async () => {
    expect(validateParentInstanceIdCharacters('', {})).toBeUndefined();

    expect(
      validateParentInstanceIdCharacters('2251799813685543', {})
    ).toBeUndefined();
    expect(
      validateParentInstanceIdCharacters('22517998136855430', {})
    ).toBeUndefined();
    expect(
      validateParentInstanceIdCharacters('225179981368554300', {})
    ).toBeUndefined();
    expect(
      validateParentInstanceIdCharacters('2251799813685543000', {})
    ).toBeUndefined();

    expect(validateParentInstanceIdCharacters('2251799813685543a', {})).toBe(
      'Key has to be a 16 to 19 digit number'
    );
    expect(validateParentInstanceIdCharacters('a', {})).toBe(
      'Key has to be a 16 to 19 digit number'
    );

    expect(validateParentInstanceIdCharacters('-', {})).toBe(
      'Key has to be a 16 to 19 digit number'
    );

    expect(
      validateParentInstanceIdCharacters(
        '2251799813685543 2251799813685543',
        {}
      )
    ).toBe('Key has to be a 16 to 19 digit number');

    expect(
      validateParentInstanceIdCharacters(
        '2251799813685543,2251799813685543',
        {}
      )
    ).toBe('Key has to be a 16 to 19 digit number');

    expect(validateParentInstanceIdNotTooLong('11111111111111111111', {})).toBe(
      'Key has to be a 16 to 19 digit number'
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate parent instance id with delay', async () => {
    expect(validateParentInstanceIdComplete('1', {})).resolves.toBe(
      'Key has to be a 16 to 19 digit number'
    );

    expect(
      validateParentInstanceIdComplete('225179981368554', {})
    ).resolves.toBe('Key has to be a 16 to 19 digit number');

    expect(setTimeoutSpy).toHaveBeenCalledTimes(2);
  });

  it('should validate date without delay', () => {
    expect(validateDateCharacters('', {})).toBeUndefined();

    expect(validateDateCharacters('a', {})).toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );

    expect(validateDateCharacters('2222-22-22 a', {})).toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );

    expect(validateDateCharacters('1111-11-11', {})).toBeUndefined();

    expect(validateDateCharacters('1111-11-11 11', {})).toBeUndefined();

    expect(validateDateCharacters('1111-11-11 11:11', {})).toBeUndefined();

    expect(validateDateCharacters('1111-11-11 11:11:11', {})).toBeUndefined();

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate date with delay', () => {
    expect(validateDateComplete('1', {})).resolves.toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );

    expect(validateDateComplete(':', {})).resolves.toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );

    expect(validateDateComplete('2222-22-22 22:22:22', {})).resolves.toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );

    expect(validateDateComplete('1111-11-', {})).resolves.toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );

    expect(validateDateComplete('1111-11-1', {})).resolves.toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );

    expect(validateDateComplete('2222-22-22 22:22:', {})).resolves.toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );

    expect(validateDateComplete('1111-11-11 11:11:', {})).resolves.toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );

    expect(validateDateComplete('1111-11-11 11:1', {})).resolves.toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );

    expect(validateDateComplete('1111-11-11 11:11:1', {})).resolves.toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );

    expect(validateDateComplete('1111-11-11 11:11:111', {})).resolves.toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );

    expect(validateDateComplete('1111-11-11 11:11::11', {})).resolves.toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(11);
  });

  it('should validate operationId without delay', () => {
    expect(validateOperationIdCharacters('', {})).toBeUndefined();
    expect(
      validateOperationIdCharacters('1f4d40c3-7cce-4e51-8abe-0cda8d42f04f', {})
    ).toBeUndefined();

    expect(validateOperationIdCharacters('&', {})).toBe('Id has to be a UUID');

    expect(validateOperationIdCharacters('g', {})).toBe('Id has to be a UUID');

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate operationId with delay', () => {
    expect(
      validateOperationIdComplete('1f4d40c3-7cce-4e51-', {})
    ).resolves.toBe('Id has to be a UUID');
    expect(
      validateOperationIdComplete('0e8481e6-b652-41c9-a72a-f531c783122', {})
    ).resolves.toBe('Id has to be a UUID');
    expect(
      validateOperationIdComplete('0e8-481e6-b652-41c9-a72a-f531c7831220', {})
    ).resolves.toBe('Id has to be a UUID');
    expect(validateOperationIdComplete('a', {})).resolves.toBe(
      'Id has to be a UUID'
    );

    expect(validateOperationIdComplete('0', {})).resolves.toBe(
      'Id has to be a UUID'
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(5);
  });

  it('should validate variable name characters without delay', () => {
    const mockMeta = {
      blur: jest.fn(),
      change: jest.fn(),
      focus: jest.fn(),
    };

    ['abc', '123'].forEach((variableName) => {
      expect(
        validateVariableNameCharacters(
          variableName,
          {newVariables: [{name: variableName}]},
          {
            ...mockMeta,
            name: 'newVariables[0].name',
          }
        )
      ).toBeUndefined();

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
          validateVariableNameCharacters(
            variableName,
            {newVariables: [{name: variableName}]},
            {
              ...mockMeta,
              name: 'newVariables[0].name',
            }
          )
        ).toBe('Name is invalid');
      });
    });
  });

  it('should validate variable name without delay', () => {
    expect(validateVariableNameComplete('', {})).toBeUndefined();
    expect(validateVariableNameComplete('test', {})).toBeUndefined();
    expect(
      validateVariableNameComplete('test', {
        variableValue: 'somethingInvalid',
      })
    ).toBeUndefined();
    expect(
      validateVariableNameComplete('test', {
        variableValue: '"somethingValid"',
      })
    ).toBeUndefined();

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate variable name with delay', () => {
    expect(
      validateVariableNameComplete('', {variableValue: '"somethingValid"'})
    ).resolves.toBe('Variable has to be filled');
    expect(
      validateVariableNameComplete('', {variableValue: '123'})
    ).resolves.toBe('Variable has to be filled');
    expect(
      validateVariableNameComplete('', {variableValue: true})
    ).resolves.toBe('Variable has to be filled');
    expect(
      validateVariableNameComplete('', {variableValue: 'somethingInvalid'})
    ).resolves.toBe('Variable has to be filled and Value has to be JSON');

    expect(setTimeoutSpy).toHaveBeenCalledTimes(4);
  });

  it('should validate variable value without delay', () => {
    expect(validateVariableValueComplete('', {})).toBeUndefined();
    expect(
      validateVariableValueComplete('{"test":123}', {variableName: 'test'})
    ).toBeUndefined();
    expect(
      validateVariableValueComplete('123', {variableName: 'test'})
    ).toBeUndefined();
    expect(
      validateVariableValueComplete('"test"', {variableName: 'test'})
    ).toBeUndefined();

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate variable value with delay', () => {
    expect(validateVariableValueComplete('1', {})).toBeUndefined();
    expect(validateVariableValueComplete('true', {})).toBeUndefined();
    expect(validateVariableValueComplete('"test"', {})).toBeUndefined();
    expect(validateVariableValueComplete('{"test": true}', {})).toBeUndefined();

    expect(
      validateVariableValueComplete('{"tes}', {variableName: 'test'})
    ).resolves.toBe('Value has to be JSON');

    expect(
      validateVariableValueComplete('', {variableName: 'test'})
    ).resolves.toBe('Value has to be JSON');

    expect(validateVariableValueComplete('a', {})).resolves.toBe(
      'Variable has to be filled and Value has to be JSON'
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(3);
  });

  it('should validate decision ids without delay ', () => {
    [
      '2251799813685543-1',
      '2251799813685543',
      '-',
      '2251799813685543-1 2251799813685543-2',
    ].forEach((decisionId) => {
      expect(validateDecisionIdsCharacters(decisionId, {})).toBeUndefined();
    });

    ['2251799813685543a', 'a', '!', ' '].forEach((decisionId) => {
      expect(validateDecisionIdsCharacters(decisionId, {})).toBe(
        'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1'
      );
    });

    [
      '2251799813685543-1',
      '2251799813685543',
      '2251799813685543-999',
      '22517998136855433213-1',
      '22517998136855433213-999',
      '2251799813685543-1 22517998136855433213-999',
    ].forEach((decisionId) => {
      expect(validateDecisionIdsLength(decisionId, {})).toBeUndefined();
    });

    ['225179981368554332130-1', '225179981368554332130-999'].forEach(
      (decisionId) => {
        expect(validateDecisionIdsLength(decisionId, {})).toBe(
          'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1'
        );
      }
    );
  });

  it('should validate decision ids with delay ', () => {
    [
      '2251799813685543-1',
      '2251799813685543-999',
      '22517998136855433213-1',
      '22517998136855433213-999',
      '2251799813685543-1 22517998136855433213-999',
    ].forEach((decisionId) => {
      expect(validatesDecisionIdsComplete(decisionId, {})).toBeUndefined();
    });

    [
      '123',
      '123-1',
      '225179981368554332133-999',
      '225179981368554',
      '22517998136855433213',
      '2251799813685542-1   000',
      '2251799813685542-1 225179981368554212',
    ].forEach((decisionId) => {
      expect(validatesDecisionIdsComplete(decisionId, {})).resolves.toBe(
        'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1'
      );
    });

    expect(setTimeoutSpy).toHaveBeenCalledTimes(7);
  });
});
