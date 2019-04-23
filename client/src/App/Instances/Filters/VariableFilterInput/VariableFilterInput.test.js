/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';

import VariableFilterInput from './VariableFilterInput';

const onFilterChange = jest.fn();

const mockDefaultProps = {onFilterChange};

const mountNode = mockCustomProps => {
  return mount(
    <ThemeProvider>
      <VariableFilterInput {...mockDefaultProps} {...mockCustomProps} />
    </ThemeProvider>
  );
};

describe('VariableFilterInput', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should update the filter on blur', () => {
    // givne
    const node = mountNode();
    const name = 'fooName';
    const value = 'fooValue';

    // when
    node
      .find('input[data-test="nameInput"]')
      .simulate('change', {target: {value: name}});
    node
      .find('input[data-test="valueInput"]')
      .simulate('change', {target: {value}});
    node.find('input[data-test="valueInput"]').simulate('blur');

    // then
    expect(onFilterChange).toBeCalledWith({variablesQuery: {name, value}});
  });
});
