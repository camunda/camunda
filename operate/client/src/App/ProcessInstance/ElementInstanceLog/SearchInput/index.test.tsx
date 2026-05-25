/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor, fireEvent} from 'modules/testing-library';
import {SearchInput} from './index';
import {elementInstanceHistorySearchStore} from 'modules/stores/elementInstanceHistorySearch';

describe('<SearchInput />', () => {
  afterEach(() => {
    elementInstanceHistorySearchStore.reset();
  });

  it('renders with the documented placeholder and aria-label', () => {
    render(<SearchInput />);
    const input = screen.getByLabelText(
      'Search element instances by name or ID',
    );
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute('placeholder', 'Search by name or ID');
  });

  it('updates the store as the user types', async () => {
    const {user} = render(<SearchInput />);
    const input = screen.getByLabelText(
      'Search element instances by name or ID',
    );
    await user.type(input, 'Order');
    await waitFor(() => {
      expect(elementInstanceHistorySearchStore.state.searchText).toBe('Order');
    });
  });

  it('clears the store via the Carbon clear button', async () => {
    elementInstanceHistorySearchStore.setSearchText('Order');
    const {user} = render(<SearchInput />);

    const clearButton = screen.getByRole('button', {name: /clear/i});
    await user.click(clearButton);

    expect(elementInstanceHistorySearchStore.state.searchText).toBe('');
  });

  it('rehydrates from the store on mount', () => {
    elementInstanceHistorySearchStore.setSearchText('Validate');
    render(<SearchInput />);
    const input = screen.getByLabelText(
      'Search element instances by name or ID',
    );
    expect(input).toHaveValue('Validate');
  });

  it('enforces a max length of 200 characters on the input', () => {
    render(<SearchInput />);
    const input = screen.getByLabelText<HTMLInputElement>(
      'Search element instances by name or ID',
    );
    expect(input).toHaveAttribute('maxLength', '200');
  });

  it('focuses the input on CMD+F / CTRL+F', () => {
    render(<SearchInput />);
    const input = screen.getByLabelText(
      'Search element instances by name or ID',
    );

    fireEvent.keyDown(document, {key: 'f', metaKey: true});
    expect(document.activeElement).toBe(input);
  });

  it('selects existing text on CMD+F when the input already has a value', () => {
    elementInstanceHistorySearchStore.setSearchText('Order');
    render(<SearchInput />);
    const input = screen.getByLabelText<HTMLInputElement>(
      'Search element instances by name or ID',
    );

    fireEvent.keyDown(document, {key: 'f', ctrlKey: true});
    expect(document.activeElement).toBe(input);
    expect(input.selectionStart).toBe(0);
    expect(input.selectionEnd).toBe(input.value.length);
  });
});
