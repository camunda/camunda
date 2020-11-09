/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {render, screen, fireEvent} from '@testing-library/react';
import {Input} from 'modules/components/Input';
import Textarea from 'modules/components/Textarea';
import {ValidationTextInput} from './index';
import {mocks} from './index.setup';

describe('ValidationTextInput', () => {
  beforeEach(() => {
    jest.resetAllMocks();
  });

  it('should render text input', () => {
    // when
    render(
      <ThemeProvider>
        <ValidationTextInput
          name="myInput"
          value="myPreset"
          onChange={() => {}}
        >
          <Input />
        </ValidationTextInput>
      </ThemeProvider>
    );

    // then
    const input = screen.getByRole('textbox') as HTMLInputElement;
    expect(input.value).toBe('myPreset');
  });

  it('should render textarea', () => {
    // when
    render(
      <ThemeProvider>
        <ValidationTextInput
          name="myInput"
          value="myPreset"
          onChange={() => {}}
        >
          <Textarea />
        </ValidationTextInput>
      </ThemeProvider>
    );

    // then
    const textarea = screen.getByRole('textbox') as HTMLInputElement;
    expect(textarea.value).toBe('myPreset');
  });

  it('should render error tooltip', async () => {
    // given
    render(
      <ThemeProvider>
        <ValidationTextInput
          onChange={mocks.onChange}
          onFilterChange={mocks.onFilterChange}
          checkIsValid={() => false}
          errorMessage="Error"
        >
          <Input />
        </ValidationTextInput>
      </ThemeProvider>
    );

    // when
    fireEvent.change(screen.getByRole('textbox'), {target: {value: 'ERROR'}});

    // then
    expect(screen.getByTitle('Error')).toBeInTheDocument();
  });

  it('should not render error tooltip', async () => {
    // given
    render(
      <ThemeProvider>
        <ValidationTextInput
          onChange={mocks.onChange}
          onFilterChange={mocks.onFilterChange}
          errorMessage="Error"
        >
          <Input />
        </ValidationTextInput>
      </ThemeProvider>
    );

    // when
    fireEvent.change(screen.getByRole('textbox'), {target: {value: 'ERROR'}});

    // then
    expect(screen.queryByTitle('Error')).not.toBeInTheDocument();
  });
});
