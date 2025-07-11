/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  validateModifiedNameComplete,
  validateModifiedNameNotDuplicate,
  validateModifiedNameNotDuplicateDeprecated,
  validateModifiedValueComplete,
  validateModifiedValueValid,
  validateNameCharacters,
  validateNameComplete,
  validateNameCompleteDeprecated,
  validateNameNotDuplicate,
  validateValueComplete,
  validateValueValid,
} from './validators';
import {variablesStore} from 'modules/stores/variables';
import type {Variable} from '@vzeta/camunda-api-zod-schemas';
import {createVariableV2} from 'modules/testUtils';

const MOCK_FIELD_META_STATE = {
  blur: vi.fn(),
  change: vi.fn(),
  focus: vi.fn(),
  name: 'fieldName',
} as const;

describe('validators with allVariables', () => {
  const allVariables: Variable[] = [
    createVariableV2({
      variableKey: '1',
      name: 'existingName1',
      value: 'value1',
    }),
    createVariableV2({
      variableKey: '2',
      name: 'existingName2',
      value: 'value2',
    }),
  ];

  describe('validateNameNotDuplicate', () => {
    it('should return undefined if name is unique', async () => {
      const result = await validateNameNotDuplicate(allVariables)(
        'uniqueName',
        {},
        {...MOCK_FIELD_META_STATE, dirty: true},
      );
      expect(result).toBeUndefined();
    });

    it('should return error if name is a duplicate in allVariables', async () => {
      const result = await validateNameNotDuplicate(allVariables)(
        'existingName2',
        {},
        {...MOCK_FIELD_META_STATE, dirty: true},
      );
      expect(result).toBe('Name should be unique');
    });

    it('should return undefined if name is a duplicate but meta is not dirty', async () => {
      const result = await validateNameNotDuplicate(allVariables)(
        'existingName2',
        {},
        {...MOCK_FIELD_META_STATE, dirty: false},
      );
      expect(result).toBeUndefined();
    });
  });

  describe('validateModifiedNameNotDuplicate', () => {
    it('should return undefined if no duplicate exists', () => {
      const result = validateModifiedNameNotDuplicate(allVariables)(
        'uniqueName',
        {newVariables: [{name: 'uniqueName'}]},
        {...MOCK_FIELD_META_STATE, dirty: true},
      );
      expect(result).toBeUndefined();
    });

    it('should return error if duplicate exists in allVariables', () => {
      const result = validateModifiedNameNotDuplicate(allVariables)(
        'existingName1',
        {newVariables: [{name: 'existingName1'}]},
        {...MOCK_FIELD_META_STATE, dirty: true},
      );
      expect(result).toBe('Name should be unique');
    });

    it('should return undefined if duplicate exists but meta is not dirty', () => {
      const result = validateModifiedNameNotDuplicate(allVariables)(
        'existingName1',
        {newVariables: [{name: 'existingName1'}]},
        {...MOCK_FIELD_META_STATE, dirty: false},
      );
      expect(result).toBeUndefined();
    });
  });

  describe('validateNameComplete', () => {
    it('should return error if name is empty and value is not empty', () => {
      const result = validateNameComplete(allVariables)(
        '',
        {value: 'someValue'},
        {...MOCK_FIELD_META_STATE},
      );
      expect(result).toBe('Name has to be filled');
    });

    it('should return error if name is a duplicate in allVariables', () => {
      const result = validateNameComplete(allVariables)(
        'existingName1',
        {value: 'someValue'},
        {...MOCK_FIELD_META_STATE, dirty: true},
      );
      expect(result).toBe('Name should be unique');
    });

    it('should return undefined if name is unique', () => {
      const result = validateNameComplete(allVariables)(
        'uniqueName',
        {value: 'someValue'},
        {...MOCK_FIELD_META_STATE, dirty: true},
      );
      expect(result).toBeUndefined();
    });
  });
});

describe('validators', () => {
  beforeEach(() => {
    vi.useFakeTimers({shouldAdvanceTime: true});
  });
  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });
  afterAll(() => {
    variablesStore.reset();
  });

  it('should validate name without delay', async () => {
    const setTimeoutSpy = vi.spyOn(global, 'setTimeout');
    expect(validateNameCharacters('', {})).toBeUndefined();
    expect(validateNameCharacters('test', {})).toBeUndefined();
    expect(validateNameCharacters('"test"', {})).toBe('Name is invalid');
    expect(validateNameCharacters('test with space', {})).toBe(
      'Name is invalid',
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate name with delay', async () => {
    const setTimeoutSpy = vi.spyOn(global, 'setTimeout');
    variablesStore.setItems([
      {
        id: '2251799813686037-clientNo',
        name: 'clientNo',
        value: '"CNT-1211132-0223222"',
        hasActiveOperation: false,
        sortValues: null,
        isPreview: false,
        isFirst: true,
      },
    ]);

    await expect(
      validateNameCompleteDeprecated('', {value: '"something"'}),
    ).resolves.toBe('Name has to be filled');

    await expect(
      validateNameCompleteDeprecated(
        'clientNo',
        {value: '"something"'},
        {...MOCK_FIELD_META_STATE, dirty: true},
      ),
    ).resolves.toBe('Name should be unique');

    expect(
      validateNameCompleteDeprecated(
        'clientNo',
        {value: '"something"'},
        {...MOCK_FIELD_META_STATE, dirty: false},
      ),
    ).toBeUndefined();

    expect(
      validateNameCompleteDeprecated('anotherName', {value: '"something"'}),
    ).toBeUndefined();

    expect(setTimeoutSpy).toHaveBeenCalledTimes(2);
  });

  it('should validate value with delay', async () => {
    const setTimeoutSpy = vi.spyOn(global, 'setTimeout');
    expect(validateValueComplete('', {name: ''})).toBeUndefined();

    expect(validateValueComplete('123', {name: 'name'})).toBeUndefined();

    expect(
      validateValueComplete('"test value"', {name: 'name'}),
    ).toBeUndefined();

    expect(
      validateValueComplete('{"foo":"bar"}', {name: 'name'}),
    ).toBeUndefined();

    expect(validateValueComplete('true', {name: 'name'})).toBeUndefined();

    expect(validateValueComplete('false', {name: 'name'})).toBeUndefined();

    expect(
      validateValueComplete('invalid json', {name: 'name'}),
    ).toBeUndefined();

    await expect(
      validateValueValid('invalid json', {name: 'name'}),
    ).resolves.toBe('Value has to be JSON');

    await expect(validateValueComplete('', {name: 'name'})).resolves.toBe(
      'Value has to be filled',
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(2);
  });

  it('should validate modified name complete', () => {
    expect(
      validateModifiedNameComplete(
        undefined,
        {newVariables: [{}]},
        {...MOCK_FIELD_META_STATE, name: 'newVariables[0]'},
      ),
    ).toBeUndefined();

    expect(
      validateModifiedNameComplete(
        undefined,
        {newVariables: [{value: '123'}]},
        {...MOCK_FIELD_META_STATE, name: 'newVariables[0]'},
      ),
    ).toBe('Name has to be filled');
  });

  it('should validate modified name not duplicate', () => {
    variablesStore.setItems([
      {
        id: '2251799813686037-clientNo',
        name: 'clientNo',
        value: '"CNT-1211132-0223222"',
        hasActiveOperation: false,
        sortValues: null,
        isPreview: false,
        isFirst: true,
      },
    ]);

    expect(
      validateModifiedNameNotDuplicateDeprecated(
        'clientNo',
        {newVariables: undefined},
        {...MOCK_FIELD_META_STATE, dirty: true},
      ),
    ).toBeUndefined();

    expect(
      validateModifiedNameNotDuplicateDeprecated(
        'clientNo',
        {newVariables: [{name: 'clientNo'}]},
        {...MOCK_FIELD_META_STATE, dirty: true},
      ),
    ).toBe('Name should be unique');

    expect(
      validateModifiedNameNotDuplicateDeprecated(
        'test',
        {newVariables: [{name: 'test'}, {name: 'test'}]},
        {...MOCK_FIELD_META_STATE, active: true},
      ),
    ).toBe('Name should be unique');

    expect(
      validateModifiedNameNotDuplicateDeprecated(
        'test',
        {newVariables: [{name: 'test'}, {name: 'test'}]},
        {...MOCK_FIELD_META_STATE, error: 'Name should be unique'},
      ),
    ).toBe('Name should be unique');

    expect(
      validateModifiedNameNotDuplicateDeprecated(
        'test',
        {newVariables: [{name: 'test'}, {name: 'test'}]},
        {...MOCK_FIELD_META_STATE, validating: true},
      ),
    ).toBe('Name should be unique');

    expect(
      validateModifiedNameNotDuplicateDeprecated(
        'test',
        {newVariables: [{name: 'test'}, {name: 'test'}]},
        {...MOCK_FIELD_META_STATE},
      ),
    ).toBeUndefined();

    expect(
      validateModifiedNameNotDuplicateDeprecated(
        'test',
        {newVariables: [{name: 'test'}]},
        {...MOCK_FIELD_META_STATE, active: true},
      ),
    ).toBeUndefined();
  });

  it('should validate modified value complete', () => {
    expect(
      validateModifiedValueComplete(
        undefined,
        {newVariables: [{}]},
        {...MOCK_FIELD_META_STATE, visited: true, name: 'newVariables[0]'},
      ),
    ).toBeUndefined();

    expect(
      validateModifiedValueComplete(
        undefined,
        {newVariables: [{value: '123'}]},
        {...MOCK_FIELD_META_STATE, visited: true, name: 'newVariables[0]'},
      ),
    ).toBeUndefined();

    expect(
      validateModifiedValueComplete(
        undefined,
        {newVariables: [{name: 'test'}]},
        {...MOCK_FIELD_META_STATE, visited: true, name: 'newVariables[0]'},
      ),
    ).toBe('Value has to be filled');

    expect(
      validateModifiedValueComplete(
        undefined,
        {newVariables: [{name: 'test'}]},
        {...MOCK_FIELD_META_STATE, name: 'newVariables[0]'},
      ),
    ).toBeUndefined();
  });

  it('should validate modified value valid', () => {
    expect(
      validateModifiedValueValid(
        undefined,
        {newVariables: [{}]},
        MOCK_FIELD_META_STATE,
      ),
    ).toBeUndefined();

    expect(
      validateModifiedValueValid(
        '{"key": "value"}',
        {newVariables: [{value: {key: 'value'}}]},
        MOCK_FIELD_META_STATE,
      ),
    ).toBeUndefined();

    expect(
      validateModifiedValueValid(
        '{invalidKey": "value"}',
        {newVariables: [{value: '{invalidKey": "value"}'}]},
        MOCK_FIELD_META_STATE,
      ),
    ).toBe('Value has to be JSON');
  });
});
