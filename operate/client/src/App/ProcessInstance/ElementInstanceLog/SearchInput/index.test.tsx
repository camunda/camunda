/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {SearchInput} from './index';

describe('<SearchInput />', () => {
  it('renders with the documented placeholder and aria-label', () => {
    render(<SearchInput value="" onChange={vi.fn()} onClear={vi.fn()} />);
    const input = screen.getByLabelText('Search element instances');
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute('placeholder', 'Element name or ID');
  });

  it('reflects the value prop in the input', () => {
    render(
      <SearchInput value="Validate" onChange={vi.fn()} onClear={vi.fn()} />,
    );
    const input = screen.getByLabelText('Search element instances');
    expect(input).toHaveValue('Validate');
  });

  it('calls onChange when the user types', async () => {
    const onChange = vi.fn();
    const {user} = render(
      <SearchInput value="" onChange={onChange} onClear={vi.fn()} />,
    );
    const input = screen.getByLabelText('Search element instances');
    await user.type(input, 'a');
    expect(onChange).toHaveBeenCalledWith('a');
  });

  it('calls onClear when the Carbon clear button is clicked', async () => {
    const onClear = vi.fn();
    const {user} = render(
      <SearchInput value="Order" onChange={vi.fn()} onClear={onClear} />,
    );
    const clearButton = screen.getByRole('button', {name: /clear/i});
    await user.click(clearButton);
    expect(onClear).toHaveBeenCalled();
  });
});
