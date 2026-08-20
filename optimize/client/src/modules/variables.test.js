/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setVariables, getVariableLabel} from './variables';

it('should retrieve a variable label based on name and type', () => {
  setVariables([
    {name: 'foo', type: 'Boolean', label: 'correctLabel'},
    {name: 'foo', type: 'String', label: 'falseLabel'},
  ]);

  expect(getVariableLabel('foo', 'Boolean')).toBe('correctLabel');
});

it('should retrieve a variable label based on name only if type is not provided', () => {
  setVariables([
    {name: 'var1', type: 'Boolean', label: 'var1Label'},
    {name: 'var2', type: 'Boolean', label: 'var2Label'},
  ]);

  expect(getVariableLabel('var1')).toBe('var1Label');
});

it('should return the name if there are no label for the variable', () => {
  setVariables([
    {name: 'var1', type: 'Boolean', label: 'var1Label'},
    {name: 'var2', type: 'Boolean', label: 'var2Label'},
  ]);

  expect(getVariableLabel('var3')).toBe('var3');
});
