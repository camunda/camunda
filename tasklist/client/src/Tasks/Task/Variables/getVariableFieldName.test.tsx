/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  getVariableFieldName,
  getNewVariablePrefix,
} from './getVariableFieldName';

describe('getVariableFieldName', () => {
  it('should get variable field name', () => {
    expect(getVariableFieldName('#someVariableName')).toBe('someVariableName');
  });
  it('should get new variable prefix', () => {
    expect(getNewVariablePrefix('newVariables[0].name')).toBe(
      'newVariables[0]',
    );
    expect(getNewVariablePrefix('newVariables[0].value')).toBe(
      'newVariables[0]',
    );
  });
});
