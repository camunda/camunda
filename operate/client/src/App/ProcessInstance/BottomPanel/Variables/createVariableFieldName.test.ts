/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  createVariableFieldName,
  createNewVariableFieldName,
} from './createVariableFieldName';

describe('createVariableFieldName', () => {
  it('should create variable field name', () => {
    expect(createVariableFieldName('someVariableName')).toBe(
      '#someVariableName',
    );
  });
  it('should create new variable field name', () => {
    expect(createNewVariableFieldName('newVariables[0]', 'name')).toBe(
      'newVariables[0].name',
    );
    expect(createNewVariableFieldName('newVariables[0]', 'value')).toBe(
      'newVariables[0].value',
    );
  });
});
