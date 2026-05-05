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

const baseMockProps = {
  isOpen: true,
  initialConditions: [] as VariableCondition[],
  onApply: vi.fn(),
  onClose: vi.fn(),
};

describe('<VariableFilterModal />', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render one empty row when no initial conditions', () => {
    render(<VariableFilterModal {...baseMockProps} />);

    expect(screen.getAllByPlaceholderText('Variable name')).toHaveLength(1);
  });

  it('should render rows for each initial condition', () => {
    const conditions: VariableCondition[] = [
      {name: 'status', operator: 'equals', value: '"active"'},
      {name: 'count', operator: 'notEqual', value: '0'},
    ];
    render(
      <VariableFilterModal {...baseMockProps} initialConditions={conditions} />,
    );

    const nameInputs = screen.getAllByPlaceholderText('Variable name');
    expect(nameInputs).toHaveLength(2);
    expect(nameInputs[0]!).toHaveValue('status');
    expect(nameInputs[1]!).toHaveValue('count');
  });

  it('should always enable Apply button', () => {
    render(<VariableFilterModal {...baseMockProps} />);

    expect(screen.getByRole('button', {name: 'Apply'})).toBeEnabled();
  });

  it('should add a new row when Add condition is clicked', async () => {
    const {user} = render(<VariableFilterModal {...baseMockProps} />);

    await user.click(screen.getByRole('button', {name: 'Add condition'}));

    expect(screen.getAllByPlaceholderText('Variable name')).toHaveLength(2);
  });

  it('should disable Add condition button when condition limit is reached', async () => {
    const conditions: VariableCondition[] = Array.from({length: 5}, (_, i) => ({
      name: `var${i}`,
      operator: 'equals' as const,
      value: `"val${i}"`,
    }));
    render(
      <VariableFilterModal {...baseMockProps} initialConditions={conditions} />,
    );

    expect(screen.getByRole('button', {name: 'Add condition'})).toBeDisabled();
  });

  it('should call onApply with valid conditions stripped of id', async () => {
    const onApply = vi.fn();
    const {user} = render(
      <VariableFilterModal {...baseMockProps} onApply={onApply} />,
    );

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'status',
    );
    await user.type(
      screen.getAllByPlaceholderText('value in JSON format')[0]!,
      '"active"',
    );
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(onApply).toHaveBeenCalledWith([
      {name: 'status', operator: 'equals', value: '"active"'},
    ]);
  });

  it('should call onClose when Cancel is clicked', async () => {
    const onClose = vi.fn();
    const {user} = render(
      <VariableFilterModal {...baseMockProps} onClose={onClose} />,
    );

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('should accept exists operator without value as valid', async () => {
    const onApply = vi.fn();
    const {user} = render(
      <VariableFilterModal {...baseMockProps} onApply={onApply} />,
    );

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'myVar',
    );

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('exists'));

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(onApply).toHaveBeenCalledWith([
      {name: 'myVar', operator: 'exists', value: ''},
    ]);
  });

  it('should accept "does not exist" operator without value as valid', async () => {
    const onApply = vi.fn();
    const {user} = render(
      <VariableFilterModal {...baseMockProps} onApply={onApply} />,
    );

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'myVar',
    );

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('does not exist'));

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(onApply).toHaveBeenCalledWith([
      {name: 'myVar', operator: 'doesNotExist', value: ''},
    ]);
  });

  it('should hide value field when operator is changed to exists', async () => {
    const {user} = render(<VariableFilterModal {...baseMockProps} />);

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
    const {user} = render(
      <VariableFilterModal {...baseMockProps} initialConditions={conditions} />,
    );

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
    render(
      <VariableFilterModal
        {...baseMockProps}
        isOpen={true}
        initialConditions={[]}
      />,
    );

    expect(screen.getAllByPlaceholderText('Variable name')).toHaveLength(1);
    expect(screen.getAllByPlaceholderText('Variable name')[0]!).toHaveValue('');
  });

  it('should show name error and keep modal open when name is empty on Apply', async () => {
    const onApply = vi.fn();
    const {user} = render(
      <VariableFilterModal {...baseMockProps} onApply={onApply} />,
    );

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(screen.getByText('Variable name is required')).toBeInTheDocument();
    expect(onApply).not.toHaveBeenCalled();
    expect(
      screen.getByRole('dialog', {name: 'Filter by Variable'}),
    ).toBeInTheDocument();
  });

  it('should show JSON error when value is not valid JSON on Apply', async () => {
    const onApply = vi.fn();
    const {user} = render(
      <VariableFilterModal {...baseMockProps} onApply={onApply} />,
    );

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'myVar',
    );
    await user.type(
      screen.getAllByPlaceholderText('value in JSON format')[0]!,
      'not-json',
    );
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(screen.getByText('Value must be valid JSON')).toBeInTheDocument();
    expect(onApply).not.toHaveBeenCalled();
  });

  it('should not show JSON error for contains operator', async () => {
    const onApply = vi.fn();
    const {user} = render(
      <VariableFilterModal {...baseMockProps} onApply={onApply} />,
    );

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'myVar',
    );
    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('contains'));
    await user.type(
      screen.getAllByPlaceholderText('search text')[0]!,
      'active',
    );

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(
      screen.queryByText('Value must be valid JSON'),
    ).not.toBeInTheDocument();
    expect(onApply).toHaveBeenCalledOnce();
  });

  it('should clear error when user starts typing after a failed submit', async () => {
    const {user} = render(<VariableFilterModal {...baseMockProps} />);

    await user.click(screen.getByRole('button', {name: 'Apply'}));
    expect(screen.getByText('Variable name is required')).toBeInTheDocument();

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'myVar',
    );

    expect(
      screen.queryByText('Variable name is required'),
    ).not.toBeInTheDocument();
  });

  it('should not show errors before a submit attempt', async () => {
    const {user} = render(<VariableFilterModal {...baseMockProps} />);

    await user.click(screen.getAllByPlaceholderText('Variable name')[0]!);
    await user.tab();

    expect(
      screen.queryByText('Variable name is required'),
    ).not.toBeInTheDocument();
  });

  it('should clear errors when a row is deleted', async () => {
    const {user} = render(<VariableFilterModal {...baseMockProps} />);

    await user.click(screen.getByRole('button', {name: 'Add condition'}));
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(screen.getAllByText('Variable name is required')).toHaveLength(2);

    const deleteButtons = screen.getAllByRole('button', {
      name: 'Remove condition',
    });
    await user.click(deleteButtons[0]!);

    expect(screen.getAllByText('Variable name is required')).toHaveLength(1);
  });

  it('should re-validate immediately when operator changes on a previously-failed row', async () => {
    const onApply = vi.fn();
    const {user} = render(
      <VariableFilterModal {...baseMockProps} onApply={onApply} />,
    );

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'myVar',
    );
    await user.click(screen.getByRole('button', {name: 'Apply'}));
    expect(screen.getByText('Value is required')).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('exists'));

    expect(screen.queryByText('Value is required')).not.toBeInTheDocument();
  });

  it('should apply successfully when all conditions are valid', async () => {
    const onApply = vi.fn();
    const {user} = render(
      <VariableFilterModal {...baseMockProps} onApply={onApply} />,
    );

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
    expect(
      screen.queryByText('Variable name is required'),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Value is required')).not.toBeInTheDocument();
  });
});
