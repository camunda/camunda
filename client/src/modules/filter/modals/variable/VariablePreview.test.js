/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import VariablePreview from './VariablePreview';

it('should combine multiple variable values with or', () => {
  const node = mount(
    <VariablePreview
      filter={{operator: 'in', values: ['varValue', 'varValue2']}}
      variableName="varName"
    />
  );

  expect(node).toIncludeText('varName is varValue or varValue2');
});

it('should combine multiple variable values with or', () => {
  const node = mount(
    <VariablePreview
      filter={{operator: 'not in', values: [null, 'varValue', 'varValue2']}}
      variableName="varName"
    />
  );

  expect(node).toIncludeText('varName is neither null nor undefined nor varValue nor varValue2');
});

it('should use less/greater for comparison operators', () => {
  const node = mount(
    <VariablePreview filter={{operator: '<', values: ['varValue']}} variableName="varName" />
  );

  expect(node).toIncludeText('varName is less than varValue');
});

it('should use contains operators', () => {
  const node = mount(
    <VariablePreview filter={{operator: 'contains', values: ['varValue']}} variableName="varName" />
  );

  expect(node).toIncludeText('varName contains varValue');
});

it('should display correct preview even if no operator is defined', () => {
  const node = mount(<VariablePreview filter={{values: [true, false]}} variableName="varName" />);

  expect(node).toIncludeText('varName is true or false');
});
