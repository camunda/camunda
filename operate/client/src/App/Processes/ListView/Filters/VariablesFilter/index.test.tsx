/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor, within} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Form} from 'react-final-form';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {Paths} from 'modules/Routes';
import {LocationLog} from 'modules/utils/LocationLog';
import {VariableFilter} from '.';
import {VariableFilterModal} from './VariableFilterModal';
import {variableFilterStore} from 'modules/stores/variableFilter';

const FormShell: React.FC<{children: React.ReactNode}> = ({children}) => (
  <Form onSubmit={() => {}}>
    {({handleSubmit}) => (
      <form onSubmit={handleSubmit}>
        <AutoSubmit />
        {children}
      </form>
    )}
  </Form>
);

const getWrapper = (initialPath = Paths.processes()) => {
  const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route
          path={Paths.processes()}
          element={
            <>
              <FormShell>{children}</FormShell>
              <LocationLog />
            </>
          }
        />
        <Route
          path={Paths.processesVariables()}
          element={
            <>
              <FormShell>{children}</FormShell>
              <VariableFilterModal />
              <LocationLog />
            </>
          }
        />
      </Routes>
    </MemoryRouter>
  );
  return Wrapper;
};

describe('<VariableFilter />', () => {
  beforeEach(() => {
    variableFilterStore.reset();
  });

  it('should render stacked Name + Value text inputs with no operator dropdown when no conditions exist', () => {
    render(<VariableFilter />, {wrapper: getWrapper()});

    expect(screen.getByTestId('single-condition-name')).toBeInTheDocument();
    expect(screen.getByTestId('single-condition-value')).toBeInTheDocument();
    expect(
      screen.queryByRole('combobox', {name: 'Operator'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('list', {name: 'Active variable filters'}),
    ).not.toBeInTheDocument();
  });

  it('should pre-fill inputs from a single stored condition', () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
    ]);

    render(<VariableFilter />, {wrapper: getWrapper()});

    expect(screen.getByTestId('single-condition-name')).toHaveValue('status');
    expect(screen.getByTestId('single-condition-value')).toHaveValue(
      '"active"',
    );
  });

  it('should auto-commit an equals condition only after BOTH name and value are filled (debounced)', async () => {
    const {user} = render(<VariableFilter />, {wrapper: getWrapper()});

    await user.type(screen.getByTestId('single-condition-name'), 'region');
    await new Promise((r) => setTimeout(r, 900));
    expect(variableFilterStore.conditions).toHaveLength(0);

    await user.type(screen.getByTestId('single-condition-value'), '"eu"');

    await waitFor(() => {
      expect(variableFilterStore.conditions).toEqual([
        {name: 'region', operator: 'equals', value: '"eu"'},
      ]);
    });
  });

  it('should commit immediately on Enter keypress when both fields are filled', async () => {
    const {user} = render(<VariableFilter />, {wrapper: getWrapper()});

    await user.type(screen.getByTestId('single-condition-name'), 'region');
    await user.type(screen.getByTestId('single-condition-value'), 'eu{Enter}');

    await waitFor(() => {
      expect(variableFilterStore.conditions).toEqual([
        {name: 'region', operator: 'equals', value: 'eu'},
      ]);
    });
  });

  it('should show "Value has to be filled" error and preserve previous filter when value is emptied', async () => {
    variableFilterStore.setConditions([
      {name: 'region', operator: 'equals', value: '"eu"'},
    ]);
    const {user} = render(<VariableFilter />, {wrapper: getWrapper()});

    await user.clear(screen.getByTestId('single-condition-value'));

    await waitFor(
      () => {
        expect(screen.getByText('Value has to be filled')).toBeInTheDocument();
      },
      {timeout: 3000},
    );
    expect(variableFilterStore.conditions).toEqual([
      {name: 'region', operator: 'equals', value: '"eu"'},
    ]);
    expect(screen.getByTestId('single-condition-name')).toHaveValue('region');
    expect(screen.getByTestId('single-condition-value')).toHaveValue('');
  });

  it('should show "Name has to be filled" error and preserve previous filter when name is emptied', async () => {
    variableFilterStore.setConditions([
      {name: 'region', operator: 'equals', value: '"eu"'},
    ]);
    const {user} = render(<VariableFilter />, {wrapper: getWrapper()});

    await user.clear(screen.getByTestId('single-condition-name'));

    await waitFor(
      () => {
        expect(screen.getByText('Name has to be filled')).toBeInTheDocument();
      },
      {timeout: 3000},
    );
    expect(variableFilterStore.conditions).toEqual([
      {name: 'region', operator: 'equals', value: '"eu"'},
    ]);
    expect(screen.getByTestId('single-condition-name')).toHaveValue('');
    expect(screen.getByTestId('single-condition-value')).toHaveValue('"eu"');
  });

  it('should clear the filter when BOTH name and value are empty', async () => {
    variableFilterStore.setConditions([
      {name: 'region', operator: 'equals', value: '"eu"'},
    ]);
    const {user} = render(<VariableFilter />, {wrapper: getWrapper()});

    await user.clear(screen.getByTestId('single-condition-name'));
    await user.clear(screen.getByTestId('single-condition-value'));

    await waitFor(() => {
      expect(variableFilterStore.conditions).toHaveLength(0);
    });
    expect(screen.queryByText('Name has to be filled')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Value has to be filled'),
    ).not.toBeInTheDocument();
  });

  it('should not show a validation error on initial empty render', () => {
    render(<VariableFilter />, {wrapper: getWrapper()});

    expect(screen.queryByText('Name has to be filled')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Value has to be filled'),
    ).not.toBeInTheDocument();
  });

  it('should not show a value error immediately after the first keystroke in name', async () => {
    const {user} = render(<VariableFilter />, {wrapper: getWrapper()});

    await user.type(screen.getByTestId('single-condition-name'), 'r');

    expect(
      screen.queryByText('Value has to be filled'),
    ).not.toBeInTheDocument();
  });

  it('should show error for unparseable value (without crashing)', async () => {
    const {user} = render(<VariableFilter />, {wrapper: getWrapper()});

    await user.type(screen.getByTestId('single-condition-name'), 'status');
    await user.type(screen.getByTestId('single-condition-value'), '"NEW');

    await waitFor(
      () => {
        expect(screen.getByText(/Invalid value/i)).toBeInTheDocument();
      },
      {timeout: 3000},
    );
    expect(variableFilterStore.conditions).toHaveLength(0);
  });

  it('should treat a whitespace-only value as empty and block the commit', async () => {
    const {user} = render(<VariableFilter />, {wrapper: getWrapper()});

    await user.type(screen.getByTestId('single-condition-name'), 'status');
    await user.type(screen.getByTestId('single-condition-value'), '   ');

    await waitFor(
      () => {
        expect(screen.getByText('Value has to be filled')).toBeInTheDocument();
      },
      {timeout: 3000},
    );
    expect(variableFilterStore.conditions).toHaveLength(0);
  });

  it('should re-sync inputs when the store is updated externally', async () => {
    variableFilterStore.setConditions([
      {name: 'old', operator: 'equals', value: '"a"'},
    ]);
    render(<VariableFilter />, {wrapper: getWrapper()});

    expect(screen.getByTestId('single-condition-name')).toHaveValue('old');
    expect(screen.getByTestId('single-condition-value')).toHaveValue('"a"');

    variableFilterStore.setConditions([
      {name: 'fresh', operator: 'equals', value: '"b"'},
    ]);

    await waitFor(() => {
      expect(screen.getByTestId('single-condition-name')).toHaveValue('fresh');
    });
    expect(screen.getByTestId('single-condition-value')).toHaveValue('"b"');
  });

  it('should render the chip list and Edit conditions button', () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
      {name: 'retries', operator: 'exists', value: ''},
    ]);

    render(<VariableFilter />, {wrapper: getWrapper()});

    expect(screen.getByText('status equals "active"')).toBeInTheDocument();
    expect(screen.getByText('retries exists')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Edit conditions'}),
    ).toBeInTheDocument();
    // Inline form is replaced by the chip list.
    expect(
      screen.queryByTestId('single-condition-name'),
    ).not.toBeInTheDocument();
  });

  it('should navigate to modal when Edit conditions clicked', async () => {
    variableFilterStore.setConditions([
      {name: 'a', operator: 'equals', value: '1'},
      {name: 'b', operator: 'equals', value: '2'},
    ]);

    const {user} = render(<VariableFilter />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Edit conditions'}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processesVariables(),
    );
    expect(
      screen.getByRole('dialog', {name: 'Filter by variable'}),
    ).toBeInTheDocument();
  });

  it('should navigate to modal when "Add condition" is clicked', async () => {
    const {user} = render(<VariableFilter />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Add condition'}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      Paths.processesVariables(),
    );
    expect(
      screen.getByRole('dialog', {name: 'Filter by variable'}),
    ).toBeInTheDocument();
  });

  it('should pre-fill the modal with the inline name+value when escalating', async () => {
    const {user} = render(<VariableFilter />, {wrapper: getWrapper()});

    await user.type(screen.getByTestId('single-condition-name'), 'status');
    await user.type(screen.getByTestId('single-condition-value'), '"active"');
    await user.tab();
    await user.click(screen.getByRole('button', {name: 'Add condition'}));

    const dialog = await screen.findByRole('dialog', {
      name: 'Filter by variable',
    });
    await waitFor(() => {
      expect(within(dialog).getByPlaceholderText('Variable name')).toHaveValue(
        'status',
      );
    });
    expect(
      within(dialog).getByPlaceholderText('e.g. true, 42, hello'),
    ).toHaveValue('"active"');
  });

  it('should not commit an empty name to the store when escalating', async () => {
    const {user} = render(<VariableFilter />, {wrapper: getWrapper()});

    await user.click(screen.getByRole('button', {name: 'Add condition'}));

    expect(variableFilterStore.conditions).toHaveLength(0);
  });

  it('should not render an Apply button (single var commits like other text filters)', () => {
    render(<VariableFilter />, {wrapper: getWrapper()});

    expect(
      screen.queryByRole('button', {name: 'Apply'}),
    ).not.toBeInTheDocument();
  });
});

it('should open modal when rendered at the modal URL', () => {
  render(<VariableFilter />, {
    wrapper: getWrapper(Paths.processesVariables()),
  });

  expect(
    screen.getByRole('dialog', {name: 'Filter by variable'}),
  ).toBeInTheDocument();
});

it('should preserve search params when opening modal via Add condition', async () => {
  const {user} = render(<VariableFilter />, {
    wrapper: getWrapper(`${Paths.processes()}?active=true&incidents=true`),
  });

  await user.click(screen.getByRole('button', {name: 'Add condition'}));

  expect(screen.getByTestId('pathname')).toHaveTextContent(
    Paths.processesVariables(),
  );
  expect(screen.getByTestId('search')).toHaveTextContent(
    '?active=true&incidents=true',
  );
});

it('should preserve search params when closing modal', async () => {
  const {user} = render(<VariableFilter />, {
    wrapper: getWrapper(
      `${Paths.processesVariables()}?active=true&incidents=true`,
    ),
  });

  expect(
    screen.getByRole('dialog', {name: 'Filter by variable'}),
  ).toBeInTheDocument();

  await user.click(screen.getByRole('button', {name: 'Cancel'}));

  await waitFor(() => {
    expect(screen.getByTestId('pathname')).toHaveTextContent(Paths.processes());
    expect(screen.getByTestId('search')).toHaveTextContent(
      '?active=true&incidents=true',
    );
  });
});
