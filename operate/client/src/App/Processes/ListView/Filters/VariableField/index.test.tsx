/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {Variable} from './index';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {ERRORS} from 'modules/validators';
import {Form} from 'react-final-form';

function getWrapper() {
  const MockWrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <Form onSubmit={() => {}}>{() => children}</Form>
  );
  return MockWrapper;
}

describe('VariableField', () => {
  beforeEach(() => {
    variableFilterStore.reset();
  });

  afterEach(() => {
    variableFilterStore.reset();
  });

  describe('adding and removing variable rows', () => {
    it('should render a single variable row initially', () => {
      render(<Variable />, {wrapper: getWrapper()});

      expect(
        screen.getAllByTestId('optional-filter-variable-name'),
      ).toHaveLength(1);
      expect(
        screen.getAllByTestId('optional-filter-variable-value'),
      ).toHaveLength(1);
      expect(
        screen.queryByRole('button', {name: /remove variable/i}),
      ).not.toBeInTheDocument();
    });

    it('should add a second variable row when "Add variable" is clicked', async () => {
      const {user} = render(<Variable />, {wrapper: getWrapper()});

      await user.click(screen.getByRole('button', {name: /add variable/i}));

      expect(
        screen.getAllByTestId('optional-filter-variable-name'),
      ).toHaveLength(2);
      expect(
        screen.getAllByTestId('optional-filter-variable-value'),
      ).toHaveLength(2);
    });

    it('should show remove buttons only when more than one variable row exists', async () => {
      const {user} = render(<Variable />, {wrapper: getWrapper()});

      expect(
        screen.queryByRole('button', {name: /remove variable/i}),
      ).not.toBeInTheDocument();

      await user.click(screen.getByRole('button', {name: /add variable/i}));

      expect(
        screen.getAllByRole('button', {name: /remove variable/i}),
      ).toHaveLength(2);
    });

    it('should remove a variable row and update the store when the remove button is clicked', async () => {
      const {user} = render(<Variable />, {wrapper: getWrapper()});

      await user.click(screen.getByRole('button', {name: /add variable/i}));

      const nameInputs = screen.getAllByTestId('optional-filter-variable-name');
      await user.type(nameInputs[0]!, 'firstVar');
      await user.type(nameInputs[1]!, 'secondVar');

      expect(variableFilterStore.variables).toHaveLength(2);

      const removeButtons = screen.getAllByRole('button', {
        name: /remove variable/i,
      });
      await user.click(removeButtons[0]!);

      expect(
        screen.getAllByTestId('optional-filter-variable-name'),
      ).toHaveLength(1);
      expect(variableFilterStore.variables).toHaveLength(1);
      expect(variableFilterStore.variables[0]!.name).toBe('secondVar');
    });

    it('should hide remove buttons after removing down to one row', async () => {
      const {user} = render(<Variable />, {wrapper: getWrapper()});

      await user.click(screen.getByRole('button', {name: /add variable/i}));

      const removeButtons = screen.getAllByRole('button', {
        name: /remove variable/i,
      });
      await user.click(removeButtons[1]!);

      expect(
        screen.queryByRole('button', {name: /remove variable/i}),
      ).not.toBeInTheDocument();
    });
  });

  describe('store updates on variable input', () => {
    it('should update the store when a variable name is typed', async () => {
      const {user} = render(<Variable />, {wrapper: getWrapper()});

      await user.type(
        screen.getByTestId('optional-filter-variable-name'),
        'myVar',
      );

      expect(variableFilterStore.variables[0]!.name).toBe('myVar');
    });

    it('should update the store when a variable value is typed', async () => {
      const {user} = render(<Variable />, {wrapper: getWrapper()});

      await user.type(
        screen.getByTestId('optional-filter-variable-value'),
        '"hello"',
      );

      expect(variableFilterStore.variables[0]!.values).toBe('"hello"');
    });

    it('should track multiple variables independently in the store', async () => {
      const {user} = render(<Variable />, {wrapper: getWrapper()});

      await user.click(screen.getByRole('button', {name: /add variable/i}));

      const nameInputs = screen.getAllByTestId('optional-filter-variable-name');
      const valueInputs = screen.getAllByTestId(
        'optional-filter-variable-value',
      );

      await user.type(nameInputs[0]!, 'varA');
      await user.type(valueInputs[0]!, '"valueA"');
      await user.type(nameInputs[1]!, 'varB');
      await user.type(valueInputs[1]!, '"valueB"');

      expect(variableFilterStore.variables).toHaveLength(2);
      expect(variableFilterStore.variables[0]).toEqual({
        name: 'varA',
        values: '"valueA"',
      });
      expect(variableFilterStore.variables[1]).toEqual({
        name: 'varB',
        values: '"valueB"',
      });
    });
  });

  describe('validation', () => {
    it('should show a name error when value is filled but name is empty', async () => {
      const {user} = render(<Variable />, {wrapper: getWrapper()});

      await user.type(
        screen.getByTestId('optional-filter-variable-value'),
        '"someValue"',
      );

      expect(
        await screen.findByText(ERRORS.variables.nameUnfilled),
      ).toBeInTheDocument();
    });

    it('should show a value error when name is filled but value is empty', async () => {
      const {user} = render(<Variable />, {wrapper: getWrapper()});

      await user.type(
        screen.getByTestId('optional-filter-variable-name'),
        'myVar',
      );

      expect(
        await screen.findByText(ERRORS.variables.valueUnfilled),
      ).toBeInTheDocument();
    });

    it('should show an invalid value error for non-JSON single value', async () => {
      const {user} = render(<Variable />, {wrapper: getWrapper()});

      const nameInput = screen.getByTestId('optional-filter-variable-name');
      const valueInput = screen.getByTestId('optional-filter-variable-value');

      await user.type(nameInput, 'myVar');
      await user.type(valueInput, 'notJSON');

      expect(
        await screen.findByText(ERRORS.variables.valueInvalid),
      ).toBeInTheDocument();
    });

    it('should validate each variable row independently', async () => {
      const {user} = render(<Variable />, {wrapper: getWrapper()});

      await user.click(screen.getByRole('button', {name: /add variable/i}));

      const nameInputs = screen.getAllByTestId('optional-filter-variable-name');
      const valueInputs = screen.getAllByTestId(
        'optional-filter-variable-value',
      );

      // First row: valid
      await user.type(nameInputs[0]!, 'validVar');
      await user.type(valueInputs[0]!, '"validValue"');

      // Second row: name empty, value filled
      await user.type(valueInputs[1]!, '"orphanValue"');

      const errorMessages = await screen.findAllByText(
        ERRORS.variables.nameUnfilled,
      );
      expect(errorMessages).toHaveLength(1);

      // First row should have no errors
      const firstRow = nameInputs[0]!.closest('li') ?? nameInputs[0]!.parentElement!;
      expect(within(firstRow).queryByText(ERRORS.variables.nameUnfilled)).not.toBeInTheDocument();
    });

    it('should show invalid multiple values error when toggle is on and values are not valid JSON list', async () => {
      const {user} = render(<Variable />, {wrapper: getWrapper()});

      await user.type(
        screen.getByTestId('optional-filter-variable-name'),
        'myVar',
      );

      await user.click(screen.getByRole('switch', {name: /multiple/i}));

      await user.type(
        screen.getByTestId('optional-filter-variable-value'),
        'notJSON,,',
      );

      expect(
        await screen.findByText(ERRORS.variables.multipleValueInvalid),
      ).toBeInTheDocument();
    });
  });

  describe('store reset on unmount', () => {
    it('should reset the store when the component unmounts', async () => {
      const {user, unmount} = render(<Variable />, {wrapper: getWrapper()});

      await user.type(
        screen.getByTestId('optional-filter-variable-name'),
        'myVar',
      );

      expect(variableFilterStore.variables[0]!.name).toBe('myVar');

      unmount();

      expect(variableFilterStore.variables).toHaveLength(0);
    });
  });
});
