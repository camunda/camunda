/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {VariableFilterInput} from './index';
import {mockDefaultProps} from './index.setup';

describe('VariableFilterInput', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should display variable provided via props', () => {
    const name = 'fooName';
    const value = 'fooValue';
    const newName = 'barName';
    const newValue = 'barValue';

    const {rerender} = render(
      <VariableFilterInput {...mockDefaultProps} variable={{name, value}} />,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByRole('textbox', {name: 'Variable'})).toHaveValue(name);
    expect(screen.getByRole('textbox', {name: 'Value'})).toHaveValue(value);

    rerender(
      <VariableFilterInput
        {...mockDefaultProps}
        variable={{name: newName, value: newValue}}
      />
    );

    expect(screen.getByRole('textbox', {name: 'Variable'})).toHaveValue(
      newName
    );
    expect(screen.getByRole('textbox', {name: 'Value'})).toHaveValue(newValue);
  });

  it('should call onChange and onFilterChange', async () => {
    render(
      <VariableFilterInput
        {...mockDefaultProps}
        variable={{name: '', value: ''}}
      />,
      {wrapper: ThemeProvider}
    );

    const name = 'fooName';
    const value = '{"a": "b"}';

    fireEvent.change(screen.getByRole('textbox', {name: 'Variable'}), {
      target: {value: 'fooName'},
    });

    fireEvent.change(screen.getByRole('textbox', {name: 'Value'}), {
      target: {value: value},
    });

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

    await waitFor(() =>
      expect(screen.getByTitle('Value has to be JSON')).toBeInTheDocument()
    );
  });

  it('should display error if value is empty', async () => {
    const {checkIsNameComplete, checkIsValueComplete} = mockDefaultProps;

    checkIsNameComplete.mockImplementation(() => true);
    checkIsValueComplete.mockImplementation(() => false);

    render(<VariableFilterInput {...mockDefaultProps} />, {
      wrapper: ThemeProvider,
    });

    const nameInput = screen.getByRole('textbox', {name: 'Variable'});

    fireEvent.change(nameInput, {target: {value: 'asdf'}});

    expect(checkIsNameComplete).toHaveBeenCalled();
    expect(checkIsValueComplete).toHaveBeenCalled();

    await waitFor(() =>
      expect(screen.getByTitle('Value has to be JSON')).toBeInTheDocument()
    );
  });

  it('should display error if value is not JSON', async () => {
    const {
      checkIsNameComplete,
      checkIsValueComplete,
      checkIsValueValid,
    } = mockDefaultProps;

    checkIsNameComplete.mockImplementation(() => true);
    checkIsValueComplete.mockImplementation(() => true);
    checkIsValueValid.mockImplementation(() => false);

    render(
      <VariableFilterInput
        {...mockDefaultProps}
        variable={{name: 'fancyName', value: '123'}}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    const nameInput = screen.getByRole('textbox', {name: 'Value'});
    fireEvent.change(nameInput, {target: {value: '{{{'}});

    expect(checkIsValueValid).toHaveBeenCalled();
    expect(checkIsNameComplete).toHaveBeenCalled();
    expect(checkIsValueComplete).toHaveBeenCalled();

    await waitFor(() =>
      expect(screen.getByTitle('Value has to be JSON')).toBeInTheDocument()
    );
  });

  it('should display error if name is empty', async () => {
    const {
      checkIsNameComplete,
      checkIsValueComplete,
      checkIsValueValid,
    } = mockDefaultProps;

    checkIsNameComplete.mockImplementation(() => false);
    checkIsValueComplete.mockImplementation(() => true);
    checkIsValueValid.mockImplementation(() => true);

    render(<VariableFilterInput {...mockDefaultProps} />, {
      wrapper: ThemeProvider,
    });

    const valueInput = screen.getByRole('textbox', {name: 'Value'});
    fireEvent.change(valueInput, {target: {value: 'something'}});

    expect(checkIsNameComplete).toHaveBeenCalled();
    expect(checkIsValueComplete).toHaveBeenCalled();
    expect(checkIsValueValid).toHaveBeenCalled();

    await waitFor(() =>
      expect(screen.getByTitle('Variable has to be filled')).toBeInTheDocument()
    );
  });

  it('should display error if both fields are empty', async () => {
    const {
      checkIsNameComplete,
      checkIsValueComplete,
      checkIsValueValid,
    } = mockDefaultProps;

    checkIsNameComplete.mockImplementation(() => false);
    checkIsValueComplete.mockImplementation(() => true);
    checkIsValueValid.mockImplementation(() => false);

    render(<VariableFilterInput {...mockDefaultProps} />, {
      wrapper: ThemeProvider,
    });

    const nameInput = screen.getByRole('textbox', {name: 'Variable'});

    fireEvent.change(nameInput, {target: {value: 'a'}});

    expect(checkIsNameComplete).toHaveBeenCalled();
    expect(checkIsValueComplete).toHaveBeenCalled();
    expect(checkIsValueValid).toHaveBeenCalled();

    await waitFor(() =>
      expect(
        screen.getByTitle('Variable has to be filled and Value has to be JSON')
      ).toBeInTheDocument()
    );
  });
});
