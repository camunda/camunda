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
  validateModifiedValueComplete,
  validateModifiedValueValid,
  validateNameCharacters,
  validateNameComplete,
  validateValueComplete,
  validateValueValid,
} from './validators';
import {variablesStore} from 'modules/stores/variables';

const MOCK_FIELD_META_STATE = {
  blur: jest.fn(),
  change: jest.fn(),
  focus: jest.fn(),
  name: 'fieldName',
} as const;

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
  afterAll(() => {
    variablesStore.reset();
  });

  it('should validate name without delay', async () => {
    expect(validateNameCharacters('', {})).toBeUndefined();
    expect(validateNameCharacters('test', {})).toBeUndefined();
    expect(validateNameCharacters('"test"', {})).toBe('Name is invalid');
    expect(validateNameCharacters('test with space', {})).toBe(
      'Name is invalid',
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(0);
  });

  it('should validate name with delay', async () => {
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

    expect(validateNameComplete('', {value: '"something"'})).resolves.toBe(
      'Name has to be filled',
    );

    expect(
      validateNameComplete(
        'clientNo',
        {value: '"something"'},
        {...MOCK_FIELD_META_STATE, dirty: true},
      ),
    ).resolves.toBe('Name should be unique');

    expect(
      validateNameComplete(
        'clientNo',
        {value: '"something"'},
        {...MOCK_FIELD_META_STATE, dirty: false},
      ),
    ).toBeUndefined();

    expect(
      validateNameComplete('anotherName', {value: '"something"'}),
    ).toBeUndefined();

    expect(setTimeoutSpy).toHaveBeenCalledTimes(2);
  });

  it('should validate value with delay', async () => {
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

    expect(validateValueValid('invalid json', {name: 'name'})).resolves.toBe(
      'Value has to be JSON',
    );

    expect(validateValueComplete('', {name: 'name'})).resolves.toBe(
      'Name has to be filled',
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
      validateModifiedNameNotDuplicate(
        'clientNo',
        {newVariables: undefined},
        {...MOCK_FIELD_META_STATE, dirty: true},
      ),
    ).toBeUndefined();

    expect(
      validateModifiedNameNotDuplicate(
        'clientNo',
        {newVariables: [{name: 'clientNo'}]},
        {...MOCK_FIELD_META_STATE, dirty: true},
      ),
    ).toBe('Name should be unique');

    expect(
      validateModifiedNameNotDuplicate(
        'test',
        {newVariables: [{name: 'test'}, {name: 'test'}]},
        {...MOCK_FIELD_META_STATE, active: true},
      ),
    ).toBe('Name should be unique');

    expect(
      validateModifiedNameNotDuplicate(
        'test',
        {newVariables: [{name: 'test'}, {name: 'test'}]},
        {...MOCK_FIELD_META_STATE, error: 'Name should be unique'},
      ),
    ).toBe('Name should be unique');

    expect(
      validateModifiedNameNotDuplicate(
        'test',
        {newVariables: [{name: 'test'}, {name: 'test'}]},
        {...MOCK_FIELD_META_STATE, validating: true},
      ),
    ).toBe('Name should be unique');

    expect(
      validateModifiedNameNotDuplicate(
        'test',
        {newVariables: [{name: 'test'}, {name: 'test'}]},
        {...MOCK_FIELD_META_STATE},
      ),
    ).toBeUndefined();

    expect(
      validateModifiedNameNotDuplicate(
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
