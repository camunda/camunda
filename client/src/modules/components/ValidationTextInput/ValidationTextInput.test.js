/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import {ThemeProvider} from 'modules/theme';
import {act} from 'react-dom/test-utils';

import Input from 'modules/components/Input';
import ValidationTextInput from './ValidationTextInput';
import {mocks} from './ValidationTextInput.setup';

describe('ValidationTextInput', () => {
  beforeEach(() => {
    jest.resetAllMocks();
  });

  it('should render', () => {
    // when
    const node = mount(
      <ThemeProvider>
        <ValidationTextInput
          name="myInput"
          value="myPreset"
          onChange={() => {}}
        />
      </ThemeProvider>
    );

    // then
    const inputNode = node.find('input');
    expect(inputNode.props().value).toEqual('myPreset');
  });

  it('should trigger onChange and onFilterChange', () => {
    // given
    const node = mount(
      <ThemeProvider>
        <ValidationTextInput
          onChange={mocks.onChange}
          onFilterChange={mocks.onFilterChange}
        />
      </ThemeProvider>
    );

    // when
    const inputNode = node.find(Input);

    inputNode.simulate('change', {
      target: {name: 'ErrorMessage', value: 'ERROR'}
    });

    // then
    expect(mocks.onChange.mock.calls[0][0].target).toEqual({
      name: 'ErrorMessage',
      value: 'ERROR'
    });
    expect(mocks.onFilterChange).toHaveBeenCalled();
  });

  it('should validate on blur immediately', () => {
    // given
    const node = mount(
      <ThemeProvider>
        <ValidationTextInput
          value={'incompleteValue'}
          onChange={mocks.onChange}
          checkIsComplete={mocks.checkIsComplete.mockImplementation(
            () => false
          )}
        />
      </ThemeProvider>
    );

    // when
    const inputNode = node.find('input');
    inputNode.simulate('blur');

    // then
    const InputNode = node.find(Input);
    expect(InputNode.props().hasError).toBe(true);
    expect(mocks.checkIsComplete).toBeCalledWith('incompleteValue');
  });

  it('should validate incomplete values', async () => {
    // given
    const node = mount(
      <ThemeProvider>
        <ValidationTextInput
          onChange={mocks.onChange}
          onFilterChange={mocks.onFilterChange}
          checkIsComplete={mocks.checkIsComplete.mockImplementation(
            () => false
          )}
        />
      </ThemeProvider>
    );

    // when
    const inputNode = node.find('input');

    await act(async () => {
      inputNode.simulate('change', {
        target: {name: 'ErrorMessage', value: 'This is an error message'}
      });
    });

    node.update();

    // then
    const InputNode = node.find(Input);
    expect(mocks.checkIsComplete).toBeCalledWith('This is an error message');
    expect(InputNode.props().hasError).toBe(true);
  });

  it('should should pass correct checkIsComplete state to Input component', async () => {
    // given
    const node = mount(
      <ThemeProvider>
        <ValidationTextInput
          value={'comple'}
          onChange={mocks.onChange}
          onFilterChange={mocks.onFilterChange}
          checkIsComplete={mocks.checkIsComplete.mockImplementation(
            value => value === 'complete'
          )}
        />
      </ThemeProvider>
    );

    // when

    node.setProps({value: 'complete'});

    const inputNode = node.find('input');

    await act(async () => {
      inputNode.simulate('change', {
        target: {name: 'ErrorMessage', value: 'complete'}
      });
    });

    node.update();

    // then
    const InputNode = node.find(Input);

    expect(mocks.checkIsComplete).toHaveBeenNthCalledWith(1, 'comple');
    expect(mocks.checkIsComplete).toHaveBeenNthCalledWith(2, 'complete');
    expect(InputNode.props().hasError).toBe(false);
  });
});
