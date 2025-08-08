/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  validateIdsCharacters,
  validatesIdsComplete,
  validateOperationIdCharacters,
  validateOperationIdComplete,
  validateVariableNameComplete,
  validateVariableValuesComplete,
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
  validateMultipleVariableValuesValid,
  ERRORS,
} from '../index';
import {mockMeta} from './mocks';

describe('validators', () => {
  beforeEach(() => {
    vi.useFakeTimers({shouldAdvanceTime: true});
  });
  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should validate ids without delay', async () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    expect(validateIdsCharacters('', {})).toBeUndefined();

    expect(validateIdsCharacters('2251799813685543', {})).toBeUndefined();
    expect(validateIdsCharacters('22517998136855430', {})).toBeUndefined();
    expect(validateIdsCharacters('225179981368554300', {})).toBeUndefined();
    expect(validateIdsCharacters('2251799813685543000', {})).toBeUndefined();

    expect(validateIdsCharacters('2251799813685543a', {})).toBe(ERRORS.ids);
    expect(validateIdsCharacters('a', {})).toBe(ERRORS.ids);

    expect(validateIdsCharacters('-', {})).toBe(ERRORS.ids);

    expect(
      validateIdsCharacters('2251799813685543 2251799813685543', {}),
    ).toBeUndefined();

    expect(
      validateIdsCharacters('2251799813685543,2251799813685543', {}),
    ).toBeUndefined();

    expect(
      validateIdsCharacters('2251799813685543, 2251799813685543', {}),
    ).toBeUndefined();

    expect(
      validateIdsCharacters(
        '2251799813685543 22517998136855430 225179981368554300 2251799813685543000',
        {},
      ),
    ).toBeUndefined();

    expect(
      validateIdsCharacters('2251799813685543 a 2251799813685543 ', {}),
    ).toBe(ERRORS.ids);

    expect(
      validateIdsCharacters('225179a9813685543 2251799813685543 ', {}),
    ).toBe(ERRORS.ids);

    expect(
      validateIdsCharacters('225179$9813685543 2251799813685543 ', {}),
    ).toBe(ERRORS.ids);

    expect(
      validateIdsLength(
        '2251799813685543 2251799813685543 11111111111111111111',
        {},
      ),
    ).toBe(ERRORS.ids);

    expect(
      validateIdsLength(
        '2251799813685543, 2251799813685543, 11111111111111111111',
        {},
      ),
    ).toBe(ERRORS.ids);

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate ids with delay', async () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    vi.runAllTimersAsync();
    await expect(
      validatesIdsComplete(
        '2251799813685543 22517998136855430 225179981368554300 2251799813685543000 22517998136855430000 22517998136855430000',
        {},
      ),
    ).resolves.toBe(ERRORS.ids);

    await expect(validatesIdsComplete('1', {})).resolves.toBe(ERRORS.ids);

    await expect(validatesIdsComplete('1 1 1 ', {})).resolves.toBe(ERRORS.ids);

    await expect(validatesIdsComplete('1', {})).resolves.toBe(ERRORS.ids);

    await expect(validatesIdsComplete('225179981368554', {})).resolves.toBe(
      ERRORS.ids,
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(5);
  });

  it('should validate parent instance id without delay', async () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    expect(validateParentInstanceIdCharacters('', {})).toBeUndefined();

    expect(
      validateParentInstanceIdCharacters('2251799813685543', {}),
    ).toBeUndefined();
    expect(
      validateParentInstanceIdCharacters('22517998136855430', {}),
    ).toBeUndefined();
    expect(
      validateParentInstanceIdCharacters('225179981368554300', {}),
    ).toBeUndefined();
    expect(
      validateParentInstanceIdCharacters('2251799813685543000', {}),
    ).toBeUndefined();

    expect(validateParentInstanceIdCharacters('2251799813685543a', {})).toBe(
      ERRORS.parentInstanceId,
    );
    expect(validateParentInstanceIdCharacters('a', {})).toBe(
      ERRORS.parentInstanceId,
    );

    expect(validateParentInstanceIdCharacters('-', {})).toBe(
      ERRORS.parentInstanceId,
    );

    expect(
      validateParentInstanceIdCharacters(
        '2251799813685543 2251799813685543',
        {},
      ),
    ).toBe(ERRORS.parentInstanceId);

    expect(
      validateParentInstanceIdCharacters(
        '2251799813685543,2251799813685543',
        {},
      ),
    ).toBe(ERRORS.parentInstanceId);

    expect(validateParentInstanceIdNotTooLong('11111111111111111111', {})).toBe(
      ERRORS.parentInstanceId,
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate parent instance id with delay', async () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    vi.runAllTimersAsync();
    await expect(validateParentInstanceIdComplete('1', {})).resolves.toBe(
      ERRORS.parentInstanceId,
    );

    await expect(
      validateParentInstanceIdComplete('225179981368554', {}),
    ).resolves.toBe(ERRORS.parentInstanceId);

    expect(setTimeoutSpy).toHaveBeenCalledTimes(2);
  });

  it('should validate operationId without delay', () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    expect(validateOperationIdCharacters('', {})).toBeUndefined();
    expect(validateOperationIdCharacters('f', {})).toBeUndefined();
    expect(validateOperationIdCharacters('1', {})).toBeUndefined();
    expect(
      validateOperationIdCharacters('1f4d40c3-7cce-4e51-8abe-0cda8d42f04f', {}),
    ).toBeUndefined();
    expect(
      validateOperationIdCharacters('2251799813685871', {}),
    ).toBeUndefined();

    expect(validateOperationIdCharacters('&', {})).toBe(ERRORS.operationId);

    expect(validateOperationIdCharacters('g', {})).toBe(ERRORS.operationId);

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate operationId with delay', async () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    vi.runAllTimersAsync();
    await expect(
      validateOperationIdComplete('1f4d40c3-7cce-4e51-', {}),
    ).resolves.toBe(ERRORS.operationId);
    await expect(
      validateOperationIdComplete('0e8481e6-b652-41c9-a72a-f531c783122', {}),
    ).resolves.toBe(ERRORS.operationId);
    await expect(
      validateOperationIdComplete('0e8-481e6-b652-41c9-a72a-f531c7831220', {}),
    ).resolves.toBe(ERRORS.operationId);
    await expect(validateOperationIdComplete('a', {})).resolves.toBe(
      ERRORS.operationId,
    );
    await expect(validateOperationIdComplete('0', {})).resolves.toBe(
      ERRORS.operationId,
    );
    await expect(validateOperationIdComplete('12345689', {})).resolves.toBe(
      ERRORS.operationId,
    );
    await expect(
      validateOperationIdComplete('01234567890123456789', {}),
    ).resolves.toBe(ERRORS.operationId);

    expect(setTimeoutSpy).toHaveBeenCalledTimes(7);
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
          },
        ),
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
            },
          ),
        ).toBe('Name is invalid');
      });
    });
  });

  it('should validate variable name without delay', () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    expect(validateVariableNameComplete('', {})).toBeUndefined();
    expect(validateVariableNameComplete('test', {})).toBeUndefined();
    expect(
      validateVariableNameComplete('test', {
        variableValues: 'somethingInvalid',
      }),
    ).toBeUndefined();
    expect(
      validateVariableNameComplete('test', {
        variableValues: '"somethingValid"',
      }),
    ).toBeUndefined();

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate variable name with delay', async () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    vi.runAllTimersAsync();
    await expect(
      validateVariableNameComplete('', {variableValues: '"somethingValid"'}),
    ).resolves.toBe(ERRORS.variables.nameUnfilled);
    await expect(
      validateVariableNameComplete('', {variableValues: '123'}),
    ).resolves.toBe(ERRORS.variables.nameUnfilled);
    await expect(
      validateVariableNameComplete('', {variableValues: true}),
    ).resolves.toBe(ERRORS.variables.nameUnfilled);
    await expect(
      validateVariableNameComplete('', {variableValues: 'somethingInvalid'}),
    ).resolves.toBe(ERRORS.variables.nameUnfilled);

    expect(setTimeoutSpy).toHaveBeenCalledTimes(4);
  });

  it('should validate variable value without delay', () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    expect(validateVariableValuesComplete('', {})).toBeUndefined();
    expect(
      validateVariableValuesComplete('{"test":123}', {variableName: 'test'}),
    ).toBeUndefined();
    expect(
      validateVariableValuesComplete('123', {variableName: 'test'}),
    ).toBeUndefined();
    expect(
      validateVariableValuesComplete('"test"', {variableName: 'test'}),
    ).toBeUndefined();

    expect(
      validateVariableValueValid('', {variableName: 'test'}),
    ).toBeUndefined();
    expect(validateVariableValuesComplete('a', {})).toBeUndefined();

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate variable value with delay', async () => {
    vi.runAllTimersAsync();
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    expect(validateVariableValuesComplete('1', {})).toBeUndefined();
    expect(validateVariableValuesComplete('true', {})).toBeUndefined();
    expect(validateVariableValuesComplete('"test"', {})).toBeUndefined();
    expect(
      validateVariableValuesComplete('{"test": true}', {}),
    ).toBeUndefined();
    expect(validateVariableValuesComplete('1, 2', {})).toBeUndefined();
    expect(
      validateVariableValuesComplete('{"a": 1}, {"b": 99}', {}),
    ).toBeUndefined();
    expect(validateVariableValuesComplete('"one","two"', {})).toBeUndefined();

    await expect(
      validateVariableValueValid('{"tes}', {variableName: 'test'}),
    ).resolves.toBe(ERRORS.variables.valueInvalid);

    await expect(
      validateVariableValuesComplete('', {variableName: 'test'}),
    ).resolves.toBe(ERRORS.variables.valueUnfilled);

    await expect(validateVariableValueValid('a', {})).resolves.toBe(
      ERRORS.variables.valueInvalid,
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(3);
  });

  it('should validate multi variable values without delay', () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    expect(
      validateMultipleVariableValuesValid('', {variableName: 'test'}),
    ).toBeUndefined();

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate multi variable values with delay', async () => {
    vi.runAllTimersAsync();
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    await expect(
      validateMultipleVariableValuesValid('2, {"tes}', {variableName: 'test'}),
    ).resolves.toBe(ERRORS.variables.multipleValueInvalid);

    await expect(validateMultipleVariableValuesValid('a,a', {})).resolves.toBe(
      ERRORS.variables.multipleValueInvalid,
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(2);
  });

  it('should validate decision ids without delay', () => {
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
        ERRORS.decisionsIds,
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
          ERRORS.decisionsIds,
        );
      },
    );
  });

  it('should validate decision ids with delay', async () => {
    vi.runAllTimersAsync();
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    [
      '2251799813685543-1',
      '2251799813685543-999',
      '22517998136855433213-1',
      '22517998136855433213-999',
      '2251799813685543-1 22517998136855433213-999',
    ].forEach((decisionId) => {
      expect(validatesDecisionIdsComplete(decisionId, {})).toBeUndefined();
    });

    for (const decisionId of [
      '123',
      '123-1',
      '225179981368554332133-999',
      '225179981368554',
      '22517998136855433213',
      '2251799813685542-1   000',
    ]) {
      await expect(validatesDecisionIdsComplete(decisionId, {})).resolves.toBe(
        ERRORS.decisionsIds,
      );
    }

    await expect(
      validatesDecisionIdsComplete('2251799813685542-1 225179981368554212', {}),
    ).resolves.toBe(ERRORS.decisionsIds);

    expect(setTimeoutSpy).toHaveBeenCalledTimes(7);
  });

  // more fine grained tests for parseFilterTime in utils/filter/index.test.ts
  it('should validate time with delay', async () => {
    vi.runAllTimersAsync();
    const validate = validateTimeComplete('99:99:99', {});
    vi.runOnlyPendingTimers();
    await expect(validate).resolves.toBe(ERRORS.time);
  });

  it('should pass validating time', () => {
    expect(validateTimeComplete('17:30', {})).toBeUndefined();
    expect(validateTimeComplete('12:34:56', {})).toBeUndefined();
  });

  it('should validate invalid characters without delay', () => {
    expect(validateTimeCharacters('a')).toBe(ERRORS.time);
    expect(validateTimeCharacters(' ')).toBe(ERRORS.time);
    expect(validateTimeCharacters('xx:xx:xx')).toBe(ERRORS.time);
    expect(validateTimeCharacters('--')).toBe(ERRORS.time);

    expect(validateTimeCharacters(':')).toBeUndefined();
    expect(validateTimeCharacters('')).toBeUndefined();
  });
});
