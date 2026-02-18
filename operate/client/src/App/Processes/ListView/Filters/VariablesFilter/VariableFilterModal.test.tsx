/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {VariableFilterModal} from './VariableFilterModal';
import {type VariableFilterCondition} from './constants';

describe('VariableFilterModal', () => {
  it('should render modal with one empty condition when open', () => {
    render(
      <VariableFilterModal isOpen={true} onClose={vi.fn()} onApply={vi.fn()} />,
    );

    expect(screen.getByTestId('variable-filter-modal')).toBeInTheDocument();
    expect(screen.getByText('Filter by variables')).toBeInTheDocument();
    expect(
      screen.getByText('Results will match all conditions.'),
    ).toBeInTheDocument();

    // given one row, the delete button should not be visible
    expect(screen.queryByLabelText('Remove condition')).not.toBeInTheDocument();
  });

  it('should not be visible when closed', () => {
    render(
      <VariableFilterModal
        isOpen={false}
        onClose={vi.fn()}
        onApply={vi.fn()}
      />,
    );

    // Carbon Modal still renders but without the open class
    expect(screen.getByTestId('variable-filter-modal')).not.toHaveClass(
      'is-visible',
    );
  });

  it('should render with initial conditions', () => {
    const initialConditions: VariableFilterCondition[] = [
      {id: '1', name: 'status', operator: 'equals', value: '"active"'},
      {id: '2', name: 'region', operator: 'contains', value: '"EU"'},
    ];

    render(
      <VariableFilterModal
        isOpen={true}
        onClose={vi.fn()}
        onApply={vi.fn()}
        initialConditions={initialConditions}
      />,
    );

    expect(screen.getByTestId('variable-filter-name-1')).toHaveValue('status');
    expect(screen.getByTestId('variable-filter-name-2')).toHaveValue('region');
    expect(screen.getByTestId('variable-filter-value-1')).toHaveValue(
      '"active"',
    );
    expect(screen.getByTestId('variable-filter-value-2')).toHaveValue('"EU"');
  });

  it('should add a new condition row when Add is clicked', async () => {
    const {user} = render(
      <VariableFilterModal isOpen={true} onClose={vi.fn()} onApply={vi.fn()} />,
    );

    await user.click(screen.getByTestId('add-variable-filter-button'));

    // given two rows, both should have a delete button
    const deleteButtons = screen.getAllByLabelText('Remove condition');
    expect(deleteButtons).toHaveLength(2);
  });

  it('should remove a condition row when delete is clicked', async () => {
    const initialConditions: VariableFilterCondition[] = [
      {id: '1', name: 'status', operator: 'equals', value: '"active"'},
      {id: '2', name: 'region', operator: 'equals', value: '"EU"'},
    ];

    const {user} = render(
      <VariableFilterModal
        isOpen={true}
        onClose={vi.fn()}
        onApply={vi.fn()}
        initialConditions={initialConditions}
      />,
    );

    expect(screen.getByTestId('variable-filter-name-1')).toBeInTheDocument();
    expect(screen.getByTestId('variable-filter-name-2')).toBeInTheDocument();

    await user.click(screen.getByTestId('delete-variable-filter-1'));

    expect(
      screen.queryByTestId('variable-filter-name-1'),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('variable-filter-name-2')).toBeInTheDocument();
  });

  it('should disable Apply when all conditions are empty', () => {
    render(
      <VariableFilterModal isOpen={true} onClose={vi.fn()} onApply={vi.fn()} />,
    );

    const modal = screen.getByTestId('variable-filter-modal');
    expect(within(modal).getByRole('button', {name: /apply/i})).toBeDisabled();
  });

  it('should enable Apply when a condition has name and value', async () => {
    const {user} = render(
      <VariableFilterModal isOpen={true} onClose={vi.fn()} onApply={vi.fn()} />,
    );

    const nameInput = screen.getByPlaceholderText('Variable name');
    const valueInput = screen.getByPlaceholderText('value in JSON format');

    await user.type(nameInput, 'status');
    await user.type(valueInput, '"active"');

    const modal = screen.getByTestId('variable-filter-modal');
    expect(within(modal).getByRole('button', {name: /apply/i})).toBeEnabled();
  });

  it('should enable Apply for exists operator with only name filled', async () => {
    const initialConditions: VariableFilterCondition[] = [
      {id: '1', name: 'status', operator: 'exists', value: ''},
    ];

    render(
      <VariableFilterModal
        isOpen={true}
        onClose={vi.fn()}
        onApply={vi.fn()}
        initialConditions={initialConditions}
      />,
    );

    const modal = screen.getByTestId('variable-filter-modal');
    expect(within(modal).getByRole('button', {name: /apply/i})).toBeEnabled();
  });

  it('should call onApply with valid conditions only', async () => {
    const onApply = vi.fn();
    const initialConditions: VariableFilterCondition[] = [
      {id: '1', name: 'status', operator: 'equals', value: '"active"'},
      {id: '2', name: '', operator: 'equals', value: ''},
    ];

    const {user} = render(
      <VariableFilterModal
        isOpen={true}
        onClose={vi.fn()}
        onApply={onApply}
        initialConditions={initialConditions}
      />,
    );

    const modal = screen.getByTestId('variable-filter-modal');
    await user.click(within(modal).getByRole('button', {name: /apply/i}));

    expect(onApply).toHaveBeenCalledWith([
      {id: '1', name: 'status', operator: 'equals', value: '"active"'},
    ]);
  });

  it('should call onClose when Cancel is clicked', async () => {
    const onClose = vi.fn();

    const {user} = render(
      <VariableFilterModal isOpen={true} onClose={onClose} onApply={vi.fn()} />,
    );

    const modal = screen.getByTestId('variable-filter-modal');
    await user.click(within(modal).getByRole('button', {name: /cancel/i}));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('should filter out conditions with empty name on apply', async () => {
    const onApply = vi.fn();
    const initialConditions: VariableFilterCondition[] = [
      {id: '1', name: '  ', operator: 'equals', value: '"active"'},
      {id: '2', name: 'region', operator: 'exists', value: ''},
    ];

    const {user} = render(
      <VariableFilterModal
        isOpen={true}
        onClose={vi.fn()}
        onApply={onApply}
        initialConditions={initialConditions}
      />,
    );

    const modal = screen.getByTestId('variable-filter-modal');
    await user.click(within(modal).getByRole('button', {name: /apply/i}));

    // given: first condition has whitespace-only name, should be filtered out
    expect(onApply).toHaveBeenCalledWith([
      {id: '2', name: 'region', operator: 'exists', value: ''},
    ]);
  });

  it('should reset conditions when reopened with different initial conditions', () => {
    const {rerender} = render(
      <VariableFilterModal
        isOpen={true}
        onClose={vi.fn()}
        onApply={vi.fn()}
        initialConditions={[
          {id: '1', name: 'status', operator: 'equals', value: '"active"'},
        ]}
      />,
    );

    expect(screen.getByTestId('variable-filter-name-1')).toHaveValue('status');

    // Close and reopen with different conditions
    rerender(
      <VariableFilterModal
        isOpen={false}
        onClose={vi.fn()}
        onApply={vi.fn()}
        initialConditions={[
          {id: '2', name: 'region', operator: 'equals', value: '"EU"'},
        ]}
      />,
    );

    rerender(
      <VariableFilterModal
        isOpen={true}
        onClose={vi.fn()}
        onApply={vi.fn()}
        initialConditions={[
          {id: '2', name: 'region', operator: 'equals', value: '"EU"'},
        ]}
      />,
    );

    expect(screen.getByTestId('variable-filter-name-2')).toHaveValue('region');
  });
});
