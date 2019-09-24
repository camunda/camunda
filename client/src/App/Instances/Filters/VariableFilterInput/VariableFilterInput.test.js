/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import {act} from 'react-dom/test-utils';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {mockResolvedAsyncFn} from 'modules/testUtils';

import VariableFilterInput from './VariableFilterInput';
import * as Styled from './styled';

const getStyledInput = (node, dataTestName) => {
  return node.find(Styled.TextInput).filter(`[data-test="${dataTestName}"]`);
};

const onFilterChange = mockResolvedAsyncFn();
const onChange = jest.fn();
const checkIsComplete = jest.fn();

const mockDefaultProps = {
  variable: {name: '', value: ''},
  onFilterChange,
  onChange,
  checkIsComplete
};

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

  it('should displayed variable provided via props', () => {
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

  it('should call onChange and onFilterChange', async () => {
    // given
    const node = mountNode({variable: {name: '', value: ''}});
    const name = 'fooName';
    const value = '{"a": "b"}';

    await act(async () => {
      // when
      node
        .find('input[data-test="nameInput"]')
        .simulate('change', {target: {name: 'name', value: name}});
      node
        .find('input[data-test="valueInput"]')
        .simulate('change', {target: {name: 'value', value: value}});
    });

    // then
    expect(onFilterChange).toHaveBeenCalledTimes(2);
    expect(onChange).toHaveBeenCalledTimes(2);
    expect(onChange).toHaveBeenNthCalledWith(1, {name, value: ''});
    expect(onChange).toHaveBeenNthCalledWith(2, {name: '', value});
  });

  it('should have input fields without error', async () => {
    // given
    const checkIsComplete = jest.fn().mockImplementation(() => true);

    const node = mountNode({
      checkIsComplete
    });

    let nameInput, valueInput;

    await act(async () => {
      // when triggering input change
      valueInput = getStyledInput(node, 'valueInput').simulate('change');
    });

    node.update();

    // then
    nameInput = getStyledInput(node, 'nameInput');
    valueInput = getStyledInput(node, 'valueInput');

    expect(checkIsComplete).toHaveBeenCalled();
    expect(nameInput.props().hasError).toBe(false);
    expect(valueInput.props().hasError).toBe(false);
  });

  it('should have both input fields with error (incomplete)', async () => {
    // given
    const checkIsComplete = jest.fn().mockImplementation(() => false);

    const node = mountNode({
      checkIsComplete
    });

    let nameInput, valueInput;

    await act(async () => {
      // when triggering input change
      getStyledInput(node, 'valueInput').simulate('change');
    });

    node.update();

    // then
    nameInput = getStyledInput(node, 'nameInput');
    valueInput = getStyledInput(node, 'valueInput');

    expect(checkIsComplete).toHaveBeenCalled();
    expect(nameInput.props().hasError).toBe(true);
    expect(valueInput.props().hasError).toBe(true);
  });

  it('should have value input field with error (invalid value)', async () => {
    // given
    const checkIsComplete = jest.fn().mockImplementation(() => true);
    const checkIsValueValid = jest.fn().mockImplementation(() => false);

    const node = mountNode({
      variable: {name: 'fancyName', value: '{{{'},
      checkIsComplete,
      checkIsValueValid
    });

    let nameInput, valueInput;

    await act(async () => {
      // when triggering input change
      getStyledInput(node, 'valueInput').simulate('change');
    });

    node.update();

    // then
    nameInput = getStyledInput(node, 'nameInput');
    valueInput = getStyledInput(node, 'valueInput');

    expect(checkIsValueValid).toHaveBeenCalled();
    expect(checkIsComplete).toHaveBeenCalled();
    expect(nameInput.props().hasError).toBe(false);
    expect(valueInput.props().hasError).toBe(true);
  });
});
