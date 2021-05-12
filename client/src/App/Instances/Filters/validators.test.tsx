/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  submissionValidator,
  handleIdsFieldValidation,
  handleStartDateFieldValidation,
  handleEndDateFieldValidation,
  handleOperationIdFieldValidation,
  handleVariableValueFieldValidation,
} from './validators';

describe('validators', () => {
  it('should validate complete values on the ids field', () => {
    expect(submissionValidator({ids: ''})).toBeNull();
    expect(submissionValidator({ids: '2251799813685543'})).toBeNull();
    expect(
      submissionValidator({ids: '2251799813685543 2251799813685543'})
    ).toBeNull();
    expect(
      submissionValidator({ids: '2251799813685543,2251799813685543'})
    ).toBeNull();
    expect(
      submissionValidator({ids: '2251799813685543, 2251799813685543'})
    ).toBeNull();
    expect(
      submissionValidator({ids: '2251799813685543, 2251799813685543'})
    ).toBeNull();
    expect(
      submissionValidator({
        ids: '2251799813685543 22517998136855430 225179981368554300 2251799813685543000',
      })
    ).toBeNull();
    expect(
      submissionValidator({
        ids: '2251799813685543 22517998136855430 225179981368554300 2251799813685543000 22517998136855430000 22517998136855430000',
      })
    ).toStrictEqual({
      ids: 'Id has to be 16 to 19 digit numbers, separated by space or comma',
    });
    expect(
      submissionValidator({
        ids: '1',
      })
    ).toStrictEqual({
      ids: 'Id has to be 16 to 19 digit numbers, separated by space or comma',
    });
    expect(
      submissionValidator({
        ids: '1 1 1 ',
      })
    ).toStrictEqual({
      ids: 'Id has to be 16 to 19 digit numbers, separated by space or comma',
    });
    expect(
      submissionValidator({
        ids: '2251799813685543 a 2251799813685543 ',
      })
    ).toStrictEqual({
      ids: 'Id has to be 16 to 19 digit numbers, separated by space or comma',
    });
    expect(
      submissionValidator({
        ids: '225179a9813685543 2251799813685543 ',
      })
    ).toStrictEqual({
      ids: 'Id has to be 16 to 19 digit numbers, separated by space or comma',
    });
    expect(
      submissionValidator({
        ids: '225179$9813685543 2251799813685543 ',
      })
    ).toStrictEqual({
      ids: 'Id has to be 16 to 19 digit numbers, separated by space or comma',
    });
  });

  it('should validate complete values on the startDate and endDate fields', () => {
    expect(
      submissionValidator({
        startDate: '',
        endDate: '',
      })
    ).toBeNull();
    expect(
      submissionValidator({
        startDate: '1111-11-11',
        endDate: '1111-11-11',
      })
    ).toBeNull();
    expect(
      submissionValidator({
        startDate: '1111-11-11 11',
        endDate: '1111-11-11 11',
      })
    ).toBeNull();
    expect(
      submissionValidator({
        startDate: '1111-11-11 11:11',
        endDate: '1111-11-11 11:11',
      })
    ).toBeNull();
    expect(
      submissionValidator({
        startDate: '1111-11-11 11:11:11',
        endDate: '1111-11-11 11:11:11',
      })
    ).toBeNull();
    expect(
      submissionValidator({
        startDate: '1111-11-11 11:11:',
        endDate: '1111-11-11 11:11:',
      })
    ).toStrictEqual({
      endDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
      startDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
    });
    expect(
      submissionValidator({
        startDate: '1111-11-11 11:1',
        endDate: '1111-11-11 11:11:1',
      })
    ).toStrictEqual({
      endDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
      startDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
    });
    expect(
      submissionValidator({
        startDate: '1111-11-1',
        endDate: '1111-11-',
      })
    ).toStrictEqual({
      endDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
      startDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
    });
    expect(
      submissionValidator({
        startDate: '1',
        endDate: '1111-11-11 11:11:111',
      })
    ).toStrictEqual({
      endDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
      startDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
    });
    expect(
      submissionValidator({
        startDate: ':',
        endDate: 'a',
      })
    ).toStrictEqual({
      endDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
      startDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
    });
    expect(
      submissionValidator({
        startDate: '1111-11-11 11:11::11',
        endDate: '1111-11-11 1a:11:11',
      })
    ).toStrictEqual({
      endDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
      startDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
    });
  });

  it('should validate complete values on the operationId field', () => {
    expect(
      submissionValidator({
        operationId: '',
      })
    ).toBeNull();
    expect(
      submissionValidator({
        operationId: '0e8481e6-b652-41c9-a72a-f531c7831220',
      })
    ).toBeNull();
    expect(
      submissionValidator({
        operationId: '0e8481e6-b652-41c9-a72a-f531c783122',
      })
    ).toStrictEqual({
      operationId: 'Id has to be a UUID',
    });
    expect(
      submissionValidator({
        operationId: '0e8-481e6-b652-41c9-a72a-f531c7831220',
      })
    ).toStrictEqual({
      operationId: 'Id has to be a UUID',
    });
    expect(
      submissionValidator({
        operationId: '0',
      })
    ).toStrictEqual({
      operationId: 'Id has to be a UUID',
    });
  });

  it('should validate complete a complete variable and value', () => {
    expect(
      submissionValidator({
        variableName: '',
        variableValue: '',
      })
    ).toBeNull();
    expect(
      submissionValidator({
        variableName: 'var',
        variableValue: '1',
      })
    ).toBeNull();
    expect(
      submissionValidator({
        variableName: 'var',
        variableValue: 'true',
      })
    ).toBeNull();
    expect(
      submissionValidator({
        variableName: 'var',
        variableValue: '"test"',
      })
    ).toBeNull();
    expect(
      submissionValidator({
        variableName: 'var',
        variableValue: '{"field": 1}',
      })
    ).toBeNull();
    expect(
      submissionValidator({
        variableName: 'var',
        variableValue: '{"field": }',
      })
    ).toStrictEqual({
      variableValue: 'Value has to be JSON',
    });
    expect(
      submissionValidator({
        variableName: 'var',
        variableValue: '',
      })
    ).toStrictEqual({
      variableValue: 'Value has to be JSON',
    });
    expect(
      submissionValidator({
        variableName: 'var',
        variableValue: 'a',
      })
    ).toStrictEqual({
      variableValue: 'Value has to be JSON',
    });
    expect(
      submissionValidator({
        variableName: '',
        variableValue: '1',
      })
    ).toStrictEqual({
      variableName: 'Variable has to be filled',
    });
    expect(
      submissionValidator({
        variableName: '',
        variableValue: 'a',
      })
    ).toStrictEqual({
      variableName: 'Variable has to be filled and Value has to be JSON',
      variableValue: 'Variable has to be filled and Value has to be JSON',
    });
  });

  it('should validate partial ids', () => {
    expect(handleIdsFieldValidation('', {})).toBeUndefined();
    expect(handleIdsFieldValidation('1', {})).toBeUndefined();
    expect(
      handleIdsFieldValidation('22517998136855430000', {})
    ).toBeUndefined();
    expect(handleIdsFieldValidation('2251799813685543a', {})).toBe(
      'Id has to be 16 to 19 digit numbers, separated by space or comma'
    );
    expect(handleIdsFieldValidation('a', {})).toBe(
      'Id has to be 16 to 19 digit numbers, separated by space or comma'
    );
    expect(handleIdsFieldValidation('-', {})).toBe(
      'Id has to be 16 to 19 digit numbers, separated by space or comma'
    );
  });

  it('should validate partial startDate and endDate', () => {
    expect(handleStartDateFieldValidation('', {})).toBeUndefined();
    expect(handleEndDateFieldValidation('', {})).toBeUndefined();

    expect(handleStartDateFieldValidation('1', {})).toBeUndefined();
    expect(handleEndDateFieldValidation('22', {})).toBeUndefined();

    expect(handleStartDateFieldValidation('1111-11-11', {})).toBeUndefined();
    expect(
      handleEndDateFieldValidation('2222-22-22 22:22:22', {})
    ).toBeUndefined();

    expect(handleStartDateFieldValidation('1111-11-', {})).toBeUndefined();
    expect(
      handleEndDateFieldValidation('2222-22-22 22:22:', {})
    ).toBeUndefined();

    expect(handleStartDateFieldValidation('a', {})).toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );
    expect(handleEndDateFieldValidation('2222-22-22 a', {})).toBe(
      'Date has to be in format YYYY-MM-DD hh:mm:ss'
    );
  });

  it('should validate a partial operationId', () => {
    expect(handleOperationIdFieldValidation('', {})).toBeUndefined();
    expect(
      handleOperationIdFieldValidation(
        '1f4d40c3-7cce-4e51-8abe-0cda8d42f04f',
        {}
      )
    ).toBeUndefined();
    expect(
      handleOperationIdFieldValidation('1f4d40c3-7cce-4e51-', {})
    ).toBeUndefined();
    expect(handleOperationIdFieldValidation('a', {})).toBeUndefined();
    expect(handleOperationIdFieldValidation('&', {})).toBe(
      'Id has to be a UUID'
    );
  });

  it('should validate a partial variableValue', () => {
    expect(handleVariableValueFieldValidation('', {})).toBeUndefined();
    expect(handleVariableValueFieldValidation('1', {})).toBeUndefined();
    expect(handleVariableValueFieldValidation('true', {})).toBeUndefined();
    expect(handleVariableValueFieldValidation('"test"', {})).toBeUndefined();
    expect(
      handleVariableValueFieldValidation('{"test": true}', {})
    ).toBeUndefined();
    expect(handleVariableValueFieldValidation('{"tes}', {})).toBe(
      'Value has to be JSON'
    );
    expect(handleVariableValueFieldValidation('a', {})).toBe(
      'Value has to be JSON'
    );
    expect(handleVariableValueFieldValidation('"a', {})).toBe(
      'Value has to be JSON'
    );
  });
});
