/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {LocationLog} from 'modules/utils/LocationLog';
import {VariableFilterModal} from './VariableFilterModal';
import {
  variableFilterStore,
  type VariableCondition,
} from 'modules/stores/variableFilter';

const getWrapper = (initialPath = Paths.processesVariables()) => {
  const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route
          path={Paths.processesVariables()}
          element={
            <>
              {children}
              <LocationLog />
            </>
          }
        />
        <Route path={Paths.processes()} element={<LocationLog />} />
      </Routes>
    </MemoryRouter>
  );
  return Wrapper;
};

describe('<VariableFilterModal />', () => {
  beforeEach(() => {
    variableFilterStore.reset();
  });

  it('should render one empty row when no initial conditions', () => {
    render(<VariableFilterModal />, {wrapper: getWrapper()});

    expect(screen.getAllByPlaceholderText('Variable name')).toHaveLength(1);
  });

  it('should render rows for each initial condition', () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
      {name: 'count', operator: 'notEqual', value: '0'},
    ]);
    render(<VariableFilterModal />, {wrapper: getWrapper()});

    const nameInputs = screen.getAllByPlaceholderText('Variable name');
    expect(nameInputs).toHaveLength(2);
    expect(nameInputs[0]!).toHaveValue('status');
    expect(nameInputs[1]!).toHaveValue('count');
  });

  it('should always enable Apply button', () => {
    render(<VariableFilterModal />, {wrapper: getWrapper()});

    expect(screen.getByRole('button', {name: 'Apply'})).toBeEnabled();
  });

  it('should add a new row when Add condition is clicked', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Add condition'}));

    expect(screen.getAllByPlaceholderText('Variable name')).toHaveLength(2);
  });

  it('should disable Add condition button when condition limit is reached', () => {
    const conditions: VariableCondition[] = Array.from({length: 5}, (_, i) => ({
      name: `var${i}`,
      operator: 'equals' as const,
      value: `"val${i}"`,
    }));
    variableFilterStore.setConditions(conditions);
    render(<VariableFilterModal />, {wrapper: getWrapper()});

    expect(screen.getByRole('button', {name: 'Add condition'})).toBeDisabled();
  });

  it('should update store and navigate on apply with valid conditions', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'status',
    );
    await user.type(
      screen.getAllByPlaceholderText('value in JSON format')[0]!,
      '"active"',
    );
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(variableFilterStore.conditions).toEqual([
      {name: 'status', operator: 'equals', value: '"active"'},
    ]);
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processes(),
    );
  });

  it('should navigate back on cancel', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processes(),
    );
  });

  it('should accept exists operator without value as valid', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'myVar',
    );

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('exists'));

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(variableFilterStore.conditions).toEqual([
      {name: 'myVar', operator: 'exists', value: ''},
    ]);
  });

  it('should accept "does not exist" operator without value as valid', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'myVar',
    );

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('does not exist'));

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(variableFilterStore.conditions).toEqual([
      {name: 'myVar', operator: 'doesNotExist', value: ''},
    ]);
  });

  it('should hide value field when operator is changed to exists', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('exists'));

    expect(
      screen.queryByPlaceholderText('value in JSON format'),
    ).not.toBeInTheDocument();
  });

  it('should remove a row when its delete button is clicked', async () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
      {name: 'count', operator: 'notEqual', value: '0'},
    ]);
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

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
    render(<VariableFilterModal />, {wrapper: getWrapper()});

    expect(screen.getAllByPlaceholderText('Variable name')).toHaveLength(1);
    expect(screen.getAllByPlaceholderText('Variable name')[0]!).toHaveValue('');
  });

  it('should show name error and keep modal open when name is empty on Apply', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(screen.getByText('Variable name is required')).toBeInTheDocument();
    expect(variableFilterStore.conditions).toHaveLength(0);
    expect(
      screen.getByRole('dialog', {name: 'Filter by Variable'}),
    ).toBeInTheDocument();
  });

  it('should show JSON error when value is not valid JSON on Apply', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

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
    expect(variableFilterStore.conditions).toHaveLength(0);
  });

  it('should not show JSON error for contains operator', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

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
    expect(variableFilterStore.conditions).toHaveLength(1);
  });

  it('should clear error when user starts typing after a failed submit', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

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
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getAllByPlaceholderText('Variable name')[0]!);
    await user.tab();

    expect(
      screen.queryByText('Variable name is required'),
    ).not.toBeInTheDocument();
  });

  it('should clear errors when a row is deleted', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

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
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

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

  it('should show value error for contains operator with empty value', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'myVar',
    );
    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('contains'));
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(screen.getByText('Value is required')).toBeInTheDocument();
    expect(variableFilterStore.conditions).toHaveLength(0);
  });

  it('should apply successfully when all conditions are valid', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.type(
      screen.getAllByPlaceholderText('Variable name')[0]!,
      'status',
    );
    await user.type(
      screen.getAllByPlaceholderText('value in JSON format')[0]!,
      '"active"',
    );
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(variableFilterStore.conditions).toHaveLength(1);
    expect(
      screen.queryByText('Variable name is required'),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Value is required')).not.toBeInTheDocument();
  });
});