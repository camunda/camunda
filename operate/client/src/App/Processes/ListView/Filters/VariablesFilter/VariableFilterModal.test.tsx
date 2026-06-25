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

    expect(screen.getAllByRole('textbox', {name: 'Name'})).toHaveLength(1);
  });

  it('should render rows for each initial condition', () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
      {name: 'count', operator: 'notEqual', value: '0'},
    ]);
    render(<VariableFilterModal />, {wrapper: getWrapper()});

    const nameInputs = screen.getAllByRole('textbox', {name: 'Name'});
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

    expect(screen.getAllByRole('textbox', {name: 'Name'})).toHaveLength(2);
  });

  it('should never disable Add condition button due to count', () => {
    const conditions: VariableCondition[] = Array.from(
      {length: 10},
      (_, i) => ({
        name: `var${i}`,
        operator: 'equals' as const,
        value: `"val${i}"`,
      }),
    );
    variableFilterStore.setConditions(conditions);
    render(<VariableFilterModal />, {wrapper: getWrapper()});

    expect(screen.getByRole('button', {name: 'Add condition'})).toBeEnabled();
  });

  it('should not show warning when adding conditions below the threshold', async () => {
    const conditions: VariableCondition[] = Array.from({length: 6}, (_, i) => ({
      name: `var${i}`,
      operator: 'equals' as const,
      value: `"val${i}"`,
    }));
    variableFilterStore.setConditions(conditions);
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Add condition'}));

    expect(
      screen.queryByText(/Filtering by many conditions/),
    ).not.toBeInTheDocument();
  });

  it('should show warning when adding the 8th condition', async () => {
    const conditions: VariableCondition[] = Array.from({length: 7}, (_, i) => ({
      name: `var${i}`,
      operator: 'equals' as const,
      value: `"val${i}"`,
    }));
    variableFilterStore.setConditions(conditions);
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Add condition'}));

    expect(
      screen.getByText(
        'Filtering by many conditions can be slow. Add conditions only if you need them.',
      ),
    ).toBeInTheDocument();
  });

  it('should show warning on initial render when conditions meet threshold', () => {
    const conditions: VariableCondition[] = Array.from({length: 8}, (_, i) => ({
      name: `var${i}`,
      operator: 'equals' as const,
      value: `"val${i}"`,
    }));
    variableFilterStore.setConditions(conditions);
    render(<VariableFilterModal />, {wrapper: getWrapper()});

    expect(
      screen.getByText(
        'Filtering by many conditions can be slow. Add conditions only if you need them.',
      ),
    ).toBeInTheDocument();
  });

  it('should update store and navigate on apply with valid conditions', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.type(
      screen.getAllByRole('textbox', {name: 'Name'})[0]!,
      'status',
    );
    await user.type(
      screen.getAllByRole('textbox', {name: 'Value'})[0]!,
      '"active"',
    );
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(variableFilterStore.conditions).toEqual([
      {name: 'status', operator: 'equals', value: '"active"'},
    ]);
    expect(screen.getByTestId('pathname')).toHaveTextContent(Paths.processes());
  });

  it('should navigate back on cancel', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(Paths.processes());
  });

  it('should accept exists operator without value as valid', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.type(
      screen.getAllByRole('textbox', {name: 'Name'})[0]!,
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
      screen.getAllByRole('textbox', {name: 'Name'})[0]!,
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
      screen.queryByRole('textbox', {name: 'Value'}),
    ).not.toBeInTheDocument();
  });

  it('should remove a row when its delete button is clicked', async () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
      {name: 'count', operator: 'notEqual', value: '0'},
    ]);
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    expect(screen.getAllByRole('textbox', {name: 'Name'})).toHaveLength(2);

    const deleteButtons = screen.getAllByRole('button', {
      name: 'Remove condition',
    });
    await user.click(deleteButtons[0]!);

    const remaining = screen.getAllByRole('textbox', {name: 'Name'});
    expect(remaining).toHaveLength(1);
    expect(remaining[0]!).toHaveValue('count');
  });

  it('should initialize with one empty row when opened with no initial conditions', () => {
    render(<VariableFilterModal />, {wrapper: getWrapper()});

    expect(screen.getAllByRole('textbox', {name: 'Name'})).toHaveLength(1);
    expect(screen.getAllByRole('textbox', {name: 'Name'})[0]!).toHaveValue('');
  });

  it('should show name error and keep modal open when name is empty on Apply', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(screen.getByText('Variable name is required')).toBeInTheDocument();
    expect(variableFilterStore.conditions).toHaveLength(0);
    expect(
      screen.getByRole('dialog', {name: 'Filter by variable'}),
    ).toBeInTheDocument();
  });

  it('should accept a bare string value via smart-transform on Apply', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.type(
      screen.getAllByRole('textbox', {name: 'Name'})[0]!,
      'myVar',
    );
    await user.type(
      screen.getAllByRole('textbox', {name: 'Value'})[0]!,
      'not-json',
    );
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(variableFilterStore.conditions).toEqual([
      {name: 'myVar', operator: 'equals', value: 'not-json'},
    ]);
  });

  it('should reject unparseable structural input (e.g. unterminated string)', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.type(
      screen.getAllByRole('textbox', {name: 'Name'})[0]!,
      'myVar',
    );
    await user.type(
      screen.getAllByRole('textbox', {name: 'Value'})[0]!,
      '{{not-json',
    );
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(screen.getByText(/Invalid value/i)).toBeInTheDocument();
    expect(variableFilterStore.conditions).toHaveLength(0);
  });

  it('should not show value error for contains operator', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.type(
      screen.getAllByRole('textbox', {name: 'Name'})[0]!,
      'myVar',
    );
    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('contains'));
    await user.type(
      screen.getAllByRole('textbox', {name: 'Value'})[0]!,
      'active',
    );

    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(screen.queryByText(/Invalid value/i)).not.toBeInTheDocument();
    expect(variableFilterStore.conditions).toHaveLength(1);
  });

  it('should clear error when user starts typing after a failed submit', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Apply'}));
    expect(screen.getByText('Variable name is required')).toBeInTheDocument();

    await user.type(
      screen.getAllByRole('textbox', {name: 'Name'})[0]!,
      'myVar',
    );

    expect(
      screen.queryByText('Variable name is required'),
    ).not.toBeInTheDocument();
  });

  it('should not show errors before a submit attempt', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getAllByRole('textbox', {name: 'Name'})[0]!);
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
      screen.getAllByRole('textbox', {name: 'Name'})[0]!,
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
      screen.getAllByRole('textbox', {name: 'Name'})[0]!,
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
      screen.getAllByRole('textbox', {name: 'Name'})[0]!,
      'status',
    );
    await user.type(
      screen.getAllByRole('textbox', {name: 'Value'})[0]!,
      '"active"',
    );
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(variableFilterStore.conditions).toHaveLength(1);
    expect(
      screen.queryByText('Variable name is required'),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Value is required')).not.toBeInTheDocument();
  });

  it('should switch to editor step when Maximize icon is clicked', async () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
    ]);
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Open JSON editor'}));

    expect(
      screen.getByRole('heading', {name: 'Edit value: status'}),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Save'})).toBeInTheDocument();
  });

  it('should show fallback heading when variable name is empty', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Open JSON editor'}));

    expect(
      screen.getByRole('heading', {name: 'Edit variable value'}),
    ).toBeInTheDocument();
  });

  it('should return to conditions step when Cancel is clicked on editor step', async () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
    ]);
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Open JSON editor'}));
    expect(
      screen.getByRole('heading', {name: 'Edit value: status'}),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(
      screen.getByRole('heading', {name: 'Filter by variable'}),
    ).toBeInTheDocument();
  });

  it('should not navigate when X button is clicked on editor step', async () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
    ]);
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Open JSON editor'}));
    await user.click(screen.getByRole('button', {name: /close/i}));

    expect(
      screen.getByRole('heading', {name: 'Filter by variable'}),
    ).toBeInTheDocument();
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processesVariables(),
    );
  });

  it('should return to conditions step when Save button is clicked', async () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"old"'},
    ]);
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Open JSON editor'}));
    expect(
      screen.getByRole('heading', {name: 'Edit value: status'}),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Save'}));

    expect(
      screen.getByRole('heading', {name: 'Filter by variable'}),
    ).toBeInTheDocument();
  });

  it('should show Copy button on editor step', async () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
    ]);
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Open JSON editor'}));

    expect(screen.getByRole('button', {name: /copy/i})).toBeInTheDocument();
  });

  it('should preserve value in text input after cancelling editor', async () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
    ]);
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    expect(screen.getByDisplayValue('"active"')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Open JSON editor'}));
    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(screen.getByDisplayValue('"active"')).toBeInTheDocument();
  });

  it('should preserve freshly typed values after opening and cancelling editor', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.type(
      screen.getAllByRole('textbox', {name: 'Name'})[0]!,
      'myVar',
    );
    await user.type(
      screen.getAllByRole('textbox', {name: 'Value'})[0]!,
      '"hello"',
    );

    await user.click(screen.getByRole('button', {name: 'Open JSON editor'}));
    expect(
      screen.getByRole('heading', {name: 'Edit value: myVar'}),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(screen.getAllByRole('textbox', {name: 'Name'})[0]!).toHaveValue(
      'myVar',
    );
    expect(screen.getAllByRole('textbox', {name: 'Value'})[0]!).toHaveValue(
      '"hello"',
    );
  });

  it('should hide conditions list on editor step', async () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
      {name: 'count', operator: 'equals', value: '5'},
    ]);
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    expect(screen.getAllByRole('textbox', {name: 'Name'})).toHaveLength(2);

    await user.click(
      screen.getAllByRole('button', {name: 'Open JSON editor'})[0]!,
    );

    screen
      .getAllByRole('textbox', {name: 'Name', hidden: true})
      .forEach((input) => {
        expect(input).not.toBeVisible();
      });
  });

  const TRUNCATION_WARNING =
    'Variable filters search only the first ~8 000 characters of a variable value. Matches in longer values may not be returned.';

  it('should show truncation warning on initial render with default equals operator', () => {
    render(<VariableFilterModal />, {wrapper: getWrapper()});
    expect(screen.getByText(TRUNCATION_WARNING)).toBeInTheDocument();
  });

  it('should show truncation warning when equals operator is selected', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByRole('option', {name: 'equals'}));

    expect(screen.getByText(TRUNCATION_WARNING)).toBeInTheDocument();
  });

  it('should show truncation warning when notEqual operator is selected', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('not equal'));

    expect(screen.getByText(TRUNCATION_WARNING)).toBeInTheDocument();
  });

  it('should show truncation warning when contains operator is selected', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('contains'));

    expect(screen.getByText(TRUNCATION_WARNING)).toBeInTheDocument();
  });

  it('should show truncation warning when oneOf operator is selected', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('is one of'));

    expect(screen.getByText(TRUNCATION_WARNING)).toBeInTheDocument();
  });

  it('should not show truncation warning when exists operator is selected', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('exists'));

    expect(screen.queryByText(TRUNCATION_WARNING)).not.toBeInTheDocument();
  });

  it('should not show truncation warning when doesNotExist operator is selected', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('does not exist'));

    expect(screen.queryByText(TRUNCATION_WARNING)).not.toBeInTheDocument();
  });

  it('should show truncation warning when one of multiple conditions uses a value-comparing operator', async () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'exists', value: ''},
    ]);
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Add condition'}));
    const operatorDropdowns = screen.getAllByRole('combobox', {
      name: 'Operator',
    });
    await user.click(operatorDropdowns[1]!);
    await user.click(screen.getByText('contains'));

    expect(screen.getByText(TRUNCATION_WARNING)).toBeInTheDocument();

    const deleteButtons = screen.getAllByRole('button', {
      name: 'Remove condition',
    });
    await user.click(deleteButtons[1]!);

    expect(screen.queryByText(TRUNCATION_WARNING)).not.toBeInTheDocument();
  });

  it('should hide truncation warning when operator is changed to exists', async () => {
    const {user} = render(<VariableFilterModal />, {wrapper: getWrapper()});

    expect(screen.getByText(TRUNCATION_WARNING)).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('exists'));

    expect(screen.queryByText(TRUNCATION_WARNING)).not.toBeInTheDocument();
  });

  it('should show truncation warning on initial render when equals condition is preloaded', () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
    ]);
    render(<VariableFilterModal />, {wrapper: getWrapper()});
    expect(screen.getByText(TRUNCATION_WARNING)).toBeInTheDocument();
  });
});
