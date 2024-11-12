/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
