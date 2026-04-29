/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {VariableFilterModal} from './VariableFilterModal';
import type {VariableCondition} from 'modules/stores/variableFilter';

const renderModal = (
  props: Partial<{
    isOpen: boolean;
    initialConditions: VariableCondition[];
    onApply: (c: VariableCondition[]) => void;
    onClose: () => void;
  }> = {},
) => {
  return render(
    <VariableFilterModal
      isOpen={props.isOpen ?? true}
      initialConditions={props.initialConditions ?? []}
      onApply={props.onApply ?? vi.fn()}
      onClose={props.onClose ?? vi.fn()}
    />,
  );
};

describe('<VariableFilterModal />', () => {
  it('should render one empty row when no initial conditions', () => {
    renderModal();

    expect(screen.getAllByPlaceholderText('Variable name')).toHaveLength(1);
  });

  it('should render rows for each initial condition', () => {
    const conditions: VariableCondition[] = [
      {name: 'status', operator: 'equals', value: '"active"'},
      {name: 'count', operator: 'notEqual', value: '0'},
    ];
    renderModal({initialConditions: conditions});

    const nameInputs = screen.getAllByPlaceholderText('Variable name');
    expect(nameInputs).toHaveLength(2);
    expect(nameInputs[0]!).toHaveValue('status');
    expect(nameInputs[1]!).toHaveValue('count');
  });

  it('should disable Apply button when all rows are empty', () => {
    renderModal();

    expect(screen.getByRole('button', {name: 'Apply'})).toBeDisabled();
  });

  it('should enable Apply button when at least one row is valid', async () => {
    const {user} = renderModal();

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'myVar',
    );
    await user.type(
      screen.getAllByPlaceholderText('value in JSON format')[0]!,
      '"hello"',
    );

    expect(screen.getByRole('button', {name: 'Apply'})).toBeEnabled();
  });

  it('should add a new row when Add condition is clicked', async () => {
    const {user} = renderModal();

    await user.click(screen.getByRole('button', {name: 'Add condition'}));

    expect(screen.getAllByPlaceholderText('Variable name')).toHaveLength(2);
  });

  it('should disable Add condition button at MAX_CONDITIONS rows', async () => {
    const conditions: VariableCondition[] = Array.from({length: 5}, (_, i) => ({
      name: `var${i}`,
      operator: 'equals' as const,
      value: `"val${i}"`,
    }));
    renderModal({initialConditions: conditions});

    expect(screen.getByRole('button', {name: 'Add condition'})).toBeDisabled();
  });

  it('should call onApply with valid conditions stripped of id', async () => {
    const onApply = vi.fn();
    const {user} = renderModal({onApply});

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'status',
    );
    await user.type(
      screen.getAllByPlaceholderText('value in JSON format')[0]!,
      '"active"',
    );
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(onApply).toHaveBeenCalledOnce();
    const applied = onApply.mock.calls[0]![0] as VariableCondition[];
    expect(applied).toHaveLength(1);
    expect(applied[0]).toEqual({
      name: 'status',
      operator: 'equals',
      value: '"active"',
    });
    expect(applied[0]).not.toHaveProperty('id');
  });

  it('should call onClose when Cancel is clicked', async () => {
    const onClose = vi.fn();
    const {user} = renderModal({onClose});

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('should filter out invalid (nameless) conditions on Apply', async () => {
    const onApply = vi.fn();
    const conditions: VariableCondition[] = [
      {name: 'status', operator: 'equals', value: '"active"'},
    ];
    const {user} = renderModal({initialConditions: conditions, onApply});

    await user.click(screen.getByRole('button', {name: 'Add condition'}));

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    const applied = onApply.mock.calls[0]![0] as VariableCondition[];
    expect(applied).toHaveLength(1);
    expect(applied[0]!.name).toBe('status');
  });

  it('should accept exists operator without value as valid', async () => {
    const onApply = vi.fn();
    const {user} = renderModal({onApply});

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'myVar',
    );

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('exists'));

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    const applied = onApply.mock.calls[0]![0] as VariableCondition[];
    expect(applied).toHaveLength(1);
    expect(applied[0]).toEqual({name: 'myVar', operator: 'exists', value: ''});
  });

  it('should accept doesNotExist operator without value as valid', async () => {
    const onApply = vi.fn();
    const {user} = renderModal({onApply});

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'myVar',
    );

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('does not exist'));

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    const applied = onApply.mock.calls[0]![0] as VariableCondition[];
    expect(applied).toHaveLength(1);
    expect(applied[0]).toEqual({
      name: 'myVar',
      operator: 'doesNotExist',
      value: '',
    });
  });

  it('should hide value field when operator is changed to exists', async () => {
    const {user} = renderModal();

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('exists'));

    expect(
      screen.queryByPlaceholderText('value in JSON format'),
    ).not.toBeInTheDocument();
  });

  it('should remove a row when its delete button is clicked', async () => {
    const conditions: VariableCondition[] = [
      {name: 'status', operator: 'equals', value: '"active"'},
      {name: 'count', operator: 'notEqual', value: '0'},
    ];
    const {user} = renderModal({initialConditions: conditions});

    expect(screen.getAllByPlaceholderText('Variable name')).toHaveLength(2);

    const deleteButtons = screen.getAllByRole('button', {
      name: 'Remove condition',
    });
    await user.click(deleteButtons[0]!);

    const remaining = screen.getAllByPlaceholderText('Variable name');
    expect(remaining).toHaveLength(1);
    expect(remaining[0]!).toHaveValue('count');
  });

  it('should initialize with one empty row when opened with no initial conditions', () => {
    renderModal({isOpen: true, initialConditions: []});

    expect(screen.getAllByPlaceholderText('Variable name')).toHaveLength(1);
    expect(screen.getAllByPlaceholderText('Variable name')[0]!).toHaveValue('');
  });
});
