/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  validateIdsCharacters,
  validatesIdsComplete,
  validateOperationIdCharacters,
  validateOperationIdComplete,
  validateVariableNameComplete,
  validateVariableValueComplete,
  validateVariableValueValid,
  validateIdsLength,
  validateParentInstanceIdCharacters,
  validateParentInstanceIdNotTooLong,
  validateParentInstanceIdComplete,
  validateVariableNameCharacters,
  validateDecisionIdsCharacters,
  validateDecisionIdsLength,
  validatesDecisionIdsComplete,
  validateTimeComplete,
  validateTimeCharacters,
} from '../index';
import {mockMeta} from './mocks';

const TIME_ERROR = 'Time has to be in format hh:mm:ss';

describe('validators', () => {
  let setTimeoutSpy: jest.SpyInstance;

  beforeEach(() => {
    jest.useFakeTimers();
    setTimeoutSpy = jest.spyOn(window, 'setTimeout');
  });
  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
    jest.restoreAllMocks();
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
    ).resolves.toBe('Name has to be filled');
    expect(
      validateVariableNameComplete('', {variableValue: '123'})
    ).resolves.toBe('Name has to be filled');
    expect(
      validateVariableNameComplete('', {variableValue: true})
    ).resolves.toBe('Name has to be filled');
    expect(
      validateVariableNameComplete('', {variableValue: 'somethingInvalid'})
    ).resolves.toBe('Name has to be filled and Value has to be JSON');

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

    expect(
      validateVariableValueValid('', {variableName: 'test'})
    ).toBeUndefined();
    expect(validateVariableValueComplete('a', {})).toBeUndefined();

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate variable value with delay', () => {
    expect(validateVariableValueComplete('1', {})).toBeUndefined();
    expect(validateVariableValueComplete('true', {})).toBeUndefined();
    expect(validateVariableValueComplete('"test"', {})).toBeUndefined();
    expect(validateVariableValueComplete('{"test": true}', {})).toBeUndefined();

    expect(
      validateVariableValueValid('{"tes}', {variableName: 'test'})
    ).resolves.toBe('Value has to be JSON');

    expect(
      validateVariableValueComplete('', {variableName: 'test'})
    ).resolves.toBe('Value has to be filled');

    expect(validateVariableValueValid('a', {})).resolves.toBe(
      'Value has to be JSON'
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

  // more fine grained tests for parseFilterTime in utils/filter/index.test.ts
  it('should validate time with delay', async () => {
    const validate = validateTimeComplete('99:99:99', {});
    jest.runOnlyPendingTimers();
    await expect(validate).resolves.toBe(TIME_ERROR);
  });

  it('should pass validating time', () => {
    expect(validateTimeComplete('17:30', {})).toBeUndefined();
    expect(validateTimeComplete('12:34:56', {})).toBeUndefined();
  });

  it('should validate invalid characters without delay', () => {
    expect(validateTimeCharacters('a')).toBe(TIME_ERROR);
    expect(validateTimeCharacters(' ')).toBe(TIME_ERROR);
    expect(validateTimeCharacters('xx:xx:xx')).toBe(TIME_ERROR);
    expect(validateTimeCharacters('--')).toBe(TIME_ERROR);

    expect(validateTimeCharacters(':')).toBeUndefined();
    expect(validateTimeCharacters('')).toBeUndefined();
  });
});
