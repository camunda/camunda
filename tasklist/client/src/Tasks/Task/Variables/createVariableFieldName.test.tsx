/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
