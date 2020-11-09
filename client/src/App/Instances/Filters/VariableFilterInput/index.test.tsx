/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import {act} from 'react-dom/test-utils';

import {ThemeProvider} from 'modules/theme/ThemeProvider';

import {VariableFilterInput} from './index';
import * as Styled from './styled';

import {mockDefaultProps} from './index.setup';

const getStyledInput = (node: any, dataTestName: any) => {
  return node.find(Styled.TextInput).filter(`[data-testid="${dataTestName}"]`);
};

const mountNode = (mockCustomProps: any) => {
  return mount(
    <ThemeProvider>
      <VariableFilterInput {...mockDefaultProps} {...mockCustomProps} />
    </ThemeProvider>
  );
};

/* Helper function as node.setProps() changes only props of the rootNode, here: <ThemeProvider>*/
const setProps = (node: any, WrappedComponent: any, updatedProps: any) => {
  return node.setProps({
    children: <WrappedComponent {...updatedProps} />,
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
      variable: {name: newName, value: newValue},
    });
    node.update();

    // then
    expect(node.find('input[data-testid="nameInput"]').props().value).toBe(
      newName
    );
    expect(node.find('input[data-testid="valueInput"]').props().value).toBe(
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
        .find('input[data-testid="nameInput"]')
        .simulate('change', {target: {name: 'name', value: name}});
      node
        .find('input[data-testid="valueInput"]')
        .simulate('change', {target: {name: 'value', value: value}});
    });

    // then
    expect(mockDefaultProps.onFilterChange).toHaveBeenCalledTimes(2);
    expect(mockDefaultProps.onChange).toHaveBeenCalledTimes(2);
    expect(mockDefaultProps.onChange).toHaveBeenNthCalledWith(1, {
      name,
      value: '',
    });
    expect(mockDefaultProps.onChange).toHaveBeenNthCalledWith(2, {
      name: '',
      value,
    });
  });

  it('should have value input field with error (incomplete value)', async () => {
    // given
    const {checkIsNameComplete, checkIsValueComplete} = mockDefaultProps;

    checkIsNameComplete.mockImplementation(() => true);
    checkIsValueComplete.mockImplementation(() => false);

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
    const node = mountNode();

    await act(async () => {
      // when triggering input change
      getStyledInput(node, 'nameInput').simulate('change');
    });

    node.update();

    // then
    const nameInput = getStyledInput(node, 'nameInput');
    const valueInput = getStyledInput(node, 'valueInput');

    expect(checkIsNameComplete).toHaveBeenCalled();
    expect(checkIsValueComplete).toHaveBeenCalled();
    expect(nameInput.props().hasError).toBe(false);
    expect(valueInput.props().hasError).toBe(true);
  });

  it('should have value input field with error (invalid value)', async () => {
    // given
    const {
      checkIsNameComplete,
      checkIsValueComplete,
      checkIsValueValid,
    } = mockDefaultProps;

    checkIsNameComplete.mockImplementation(() => true);
    checkIsValueComplete.mockImplementation(() => true);
    checkIsValueValid.mockImplementation(() => false);

    const node = mountNode({
      variable: {name: 'fancyName', value: '{{{'},
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
    expect(checkIsNameComplete).toHaveBeenCalled();
    expect(checkIsValueComplete).toHaveBeenCalled();
    expect(nameInput.props().hasError).toBe(false);
    expect(valueInput.props().hasError).toBe(true);
  });

  it('should have name input field with error', async () => {
    // given
    const {
      checkIsNameComplete,
      checkIsValueComplete,
      checkIsValueValid,
    } = mockDefaultProps;

    checkIsNameComplete.mockImplementation(() => false);
    checkIsValueComplete.mockImplementation(() => true);
    checkIsValueValid.mockImplementation(() => true);

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
    const node = mountNode();

    await act(async () => {
      // when triggering input change
      getStyledInput(node, 'valueInput').simulate('change');
    });

    node.update();

    // then
    const nameInput = getStyledInput(node, 'nameInput');
    const valueInput = getStyledInput(node, 'valueInput');

    expect(checkIsNameComplete).toHaveBeenCalled();
    expect(checkIsValueComplete).toHaveBeenCalled();
    expect(checkIsValueValid).toHaveBeenCalled();
    expect(nameInput.props().hasError).toBe(true);
    expect(valueInput.props().hasError).toBe(false);
  });

  it('should have both input fields with error (incomplete)', async () => {
    // given
    const {checkIsNameComplete, checkIsValueComplete} = mockDefaultProps;

    checkIsNameComplete.mockImplementation(() => false);
    checkIsValueComplete.mockImplementation(() => false);

    // @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
    const node = mountNode();

    let nameInput, valueInput;

    await act(async () => {
      // when triggering input change
      getStyledInput(node, 'valueInput').simulate('change');
    });

    node.update();

    // then
    nameInput = getStyledInput(node, 'nameInput');
    valueInput = getStyledInput(node, 'valueInput');

    expect(checkIsNameComplete).toHaveBeenCalled();
    expect(checkIsValueComplete).toHaveBeenCalled();
    expect(nameInput.props().hasError).toBe(true);
    expect(valueInput.props().hasError).toBe(true);
  });
});
