/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {VariableFilterRow} from './VariableFilterRow';
import {type VariableFilterCondition} from './constants';

const mockCondition: VariableFilterCondition = {
  id: 'test-1',
  name: '',
  operator: 'equals',
  value: '',
};

describe('VariableFilterRow', () => {
  it('should render name input, operator dropdown, and value input', () => {
    render(
      <VariableFilterRow
        condition={mockCondition}
        onChange={vi.fn()}
        onDelete={vi.fn()}
        isDeleteDisabled={false}
        rowIndex={0}
      />,
    );

    expect(
      screen.getByTestId('variable-filter-name-test-1'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('variable-filter-operator-test-1'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('variable-filter-value-test-1'),
    ).toBeInTheDocument();
  });

  it('should call onChange when name is changed', async () => {
    const onChange = vi.fn();

    const {user} = render(
      <VariableFilterRow
        condition={mockCondition}
        onChange={onChange}
        onDelete={vi.fn()}
        isDeleteDisabled={false}
        rowIndex={0}
      />,
    );

    await user.type(
      screen.getByTestId('variable-filter-name-test-1'),
      'status',
    );

    expect(onChange).toHaveBeenCalled();
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({name: 's'}),
    );
  });

  it('should call onChange when value is changed', async () => {
    const onChange = vi.fn();
    const conditionWithName: VariableFilterCondition = {
      ...mockCondition,
      name: 'status',
    };

    const {user} = render(
      <VariableFilterRow
        condition={conditionWithName}
        onChange={onChange}
        onDelete={vi.fn()}
        isDeleteDisabled={false}
        rowIndex={0}
      />,
    );

    await user.type(
      screen.getByTestId('variable-filter-value-test-1'),
      '"a"',
    );

    expect(onChange).toHaveBeenCalled();
  });

  it('should hide value field for exists operator', () => {
    const existsCondition: VariableFilterCondition = {
      ...mockCondition,
      operator: 'exists',
    };

    render(
      <VariableFilterRow
        condition={existsCondition}
        onChange={vi.fn()}
        onDelete={vi.fn()}
        isDeleteDisabled={false}
        rowIndex={0}
      />,
    );

    expect(
      screen.queryByTestId('variable-filter-value-test-1'),
    ).not.toBeInTheDocument();
  });

  it('should hide value field for doesNotExist operator', () => {
    const doesNotExistCondition: VariableFilterCondition = {
      ...mockCondition,
      operator: 'doesNotExist',
    };

    render(
      <VariableFilterRow
        condition={doesNotExistCondition}
        onChange={vi.fn()}
        onDelete={vi.fn()}
        isDeleteDisabled={false}
        rowIndex={0}
      />,
    );

    expect(
      screen.queryByTestId('variable-filter-value-test-1'),
    ).not.toBeInTheDocument();
  });

  it('should call onDelete when delete button is clicked', async () => {
    const onDelete = vi.fn();

    const {user} = render(
      <VariableFilterRow
        condition={mockCondition}
        onChange={vi.fn()}
        onDelete={onDelete}
        isDeleteDisabled={false}
        rowIndex={0}
      />,
    );

    await user.click(
      screen.getByTestId('delete-variable-filter-test-1'),
    );

    expect(onDelete).toHaveBeenCalledTimes(1);
  });

  it('should not show delete button when isDeleteDisabled is true', () => {
    render(
      <VariableFilterRow
        condition={mockCondition}
        onChange={vi.fn()}
        onDelete={vi.fn()}
        isDeleteDisabled={true}
        rowIndex={0}
      />,
    );

    expect(
      screen.queryByTestId('delete-variable-filter-test-1'),
    ).not.toBeInTheDocument();
  });

  it('should open JSON editor modal when expand button is clicked', async () => {
    const conditionWithName: VariableFilterCondition = {
      ...mockCondition,
      name: 'status',
    };

    const {user} = render(
      <VariableFilterRow
        condition={conditionWithName}
        onChange={vi.fn()}
        onDelete={vi.fn()}
        isDeleteDisabled={false}
        rowIndex={0}
      />,
    );

    await user.click(
      screen.getByRole('button', {name: /open json editor/i}),
    );

    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
