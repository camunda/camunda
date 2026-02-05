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
<<<<<<< HEAD:tasklist/client/src/common/tasks/variables-editor/createVariableFieldName.test.tsx

  it('should escape dots in variable names', () => {
    expect(createVariableFieldName('some.variable.name')).toBe(
      '#some___DOT___variable___DOT___name',
    );
  });

=======
  it('should encode variable field name with dots', () => {
    expect(createVariableFieldName('a.b')).toBe('#a%2Eb');
  });
>>>>>>> c857b356 (fix: variable form when variables have dots in the name):tasklist/client/src/Tasks/Task/Variables/createVariableFieldName.test.tsx
  it('should create new variable field name', () => {
    expect(createNewVariableFieldName('newVariables[0]', 'name')).toBe(
      'newVariables[0].name',
    );
    expect(createNewVariableFieldName('newVariables[0]', 'value')).toBe(
      'newVariables[0].value',
    );
  });
});
