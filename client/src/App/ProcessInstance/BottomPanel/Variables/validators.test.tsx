/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  validateNameCharacters,
  validateNameComplete,
  validateValueComplete,
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
  });
  afterAll(() => {
    variablesStore.reset();
  });

  it('should validate name without delay', async () => {
    expect(validateNameCharacters('', {})).toBeUndefined();
    expect(validateNameCharacters('test', {})).toBeUndefined();
    expect(validateNameCharacters('"test"', {})).toBe('Name is invalid');
    expect(validateNameCharacters('test with space', {})).toBe(
      'Name is invalid'
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
      'Name has to be filled'
    );

    expect(
      validateNameComplete(
        'clientNo',
        {value: '"something"'},
        {...MOCK_FIELD_META_STATE, dirty: true}
      )
    ).resolves.toBe('Name should be unique');

    expect(
      validateNameComplete(
        'clientNo',
        {value: '"something"'},
        {...MOCK_FIELD_META_STATE, dirty: false}
      )
    ).toBeUndefined();

    expect(
      validateNameComplete('anotherName', {value: '"something"'})
    ).toBeUndefined();

    expect(setTimeoutSpy).toHaveBeenCalledTimes(2);
  });

  it('should validate value with delay', async () => {
    expect(validateValueComplete('', {name: ''})).toBeUndefined();

    expect(validateValueComplete('123', {name: 'name'})).toBeUndefined();

    expect(
      validateValueComplete('"test value"', {name: 'name'})
    ).toBeUndefined();

    expect(
      validateValueComplete('{"foo":"bar"}', {name: 'name'})
    ).toBeUndefined();

    expect(validateValueComplete('true', {name: 'name'})).toBeUndefined();

    expect(validateValueComplete('false', {name: 'name'})).toBeUndefined();

    expect(validateValueComplete('invalid json', {name: 'name'})).resolves.toBe(
      'Invalid input text'
    );

    expect(validateValueComplete('', {name: 'name'})).resolves.toBe(
      'Invalid input text'
    );

    expect(setTimeoutSpy).toHaveBeenCalledTimes(2);
  });
});
