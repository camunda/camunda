/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
