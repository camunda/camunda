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
<<<<<<< HEAD:tasklist/client/src/common/tasks/variables-editor/getVariableFieldName.test.tsx

  it('should unescape dots in variable names', () => {
    expect(getVariableFieldName('#some___DOT___variable___DOT___name')).toBe(
      'some.variable.name',
    );
  });

=======
  it('should decode variable field name with dots', () => {
    expect(getVariableFieldName('#a%2Eb')).toBe('a.b');
  });
>>>>>>> c857b356 (fix: variable form when variables have dots in the name):tasklist/client/src/Tasks/Task/Variables/getVariableFieldName.test.tsx
  it('should get new variable prefix', () => {
    expect(getNewVariablePrefix('newVariables[0].name')).toBe(
      'newVariables[0]',
    );
    expect(getNewVariablePrefix('newVariables[0].value')).toBe(
      'newVariables[0]',
    );
  });
});
