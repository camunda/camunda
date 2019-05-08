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

/* Helper function as node.setProps() changes only props of the rootNode, here: <ThemeProvider>*/
const setProps = (node, WrappedComponent, updatedProps) => {
  return node.setProps({
    children: <WrappedComponent {...updatedProps} />
  });
};

describe('VariableFilterInput', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it.skip('should displayed variable provided via props', () => {
    // given
    const name = 'fooName';
    const value = 'fooValue';
    const newName = 'barName';
    const newValue = 'barValue';
    const node = mountNode({variable: {name, value}});

    // when
    setProps(node, VariableFilterInput, {
      ...mockDefaultProps,
      variable: {name: newName, value: newValue}
    });
    node.update();

    // then
    expect(node.find('input[data-test="nameInput"]').props().value).toBe(
      newName
    );
    expect(node.find('input[data-test="valueInput"]').props().value).toBe(
      newValue
    );
  });

  it('should update the filter on blur', () => {
    // given
    const node = mountNode({variable: {name: '', value: ''}});
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
    expect(onFilterChange).toBeCalledWith({variable: {name, value}});
  });

  it('should only update filter when name and value exist', () => {
    // given
    const node = mountNode({variable: {name: '', value: ''}});
    const name = 'fooName';

    // when
    node
      .find('input[data-test="nameInput"]')
      .simulate('change', {target: {value: name}});
    node.find('input[data-test="valueInput"]').simulate('blur');

    // then
    expect(onFilterChange).toBeCalledWith({variable: null});
  });
});
