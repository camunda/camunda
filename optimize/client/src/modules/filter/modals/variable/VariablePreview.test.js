/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import VariablePreview from './VariablePreview';

it('should combine multiple variable values with or', () => {
  const node = shallow(
    <VariablePreview
      filter={{operator: 'in', values: ['varValue', 'varValue2']}}
      variableName="varName"
    />
  );

  expect(node).toMatchSnapshot();
});

it('should combine multiple variable values with or', () => {
  const node = shallow(
    <VariablePreview
      filter={{operator: 'not in', values: [null, 'varValue', 'varValue2']}}
      variableName="varName"
    />
  );

  expect(node).toMatchSnapshot();
});

it('should use less/greater for comparison operators', () => {
  const node = shallow(
    <VariablePreview filter={{operator: '<', values: ['varValue']}} variableName="varName" />
  );

  expect(node).toMatchSnapshot();
});

it('should use contains operators', () => {
  const node = shallow(
    <VariablePreview filter={{operator: 'contains', values: ['varValue']}} variableName="varName" />
  );

  expect(node).toMatchSnapshot();
});

it('should display correct preview even if no operator is defined', () => {
  const node = shallow(<VariablePreview filter={{values: [true, false]}} variableName="varName" />);

  expect(node).toMatchSnapshot();
});
