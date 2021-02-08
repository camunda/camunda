/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createVariableFieldName} from './createVariableFieldName';

describe('createVariableFieldName', () => {
  it('should create variable field name', () => {
    expect(createVariableFieldName('someVariableName')).toBe(
      '#someVariableName',
    );
  });
});
