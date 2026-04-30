/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {VariableFilterRow} from './VariableFilterRow';
import type {DraftCondition} from './constants';

const makeCondition = (
  overrides: Partial<DraftCondition> = {},
): DraftCondition => ({
  id: 'test-id',
  name: '',
  operator: 'equals',
  value: '',
  ...overrides,
});

let defaultRowProps: {errors: object; onBlur: () => void};

beforeEach(() => {
  defaultRowProps = {errors: {}, onBlur: vi.fn()};
});

describe('<VariableFilterRow />', () => {
  it('should render name and value inputs and operator dropdown', () => {
    render(
      <VariableFilterRow
        condition={makeCondition()}
        onChange={vi.fn()}
        onDelete={vi.fn()}
        isDeleteHidden={true}
        rowIndex={0}
        {...defaultRowProps}
      />,
    );

    expect(
      screen.getByTestId('variable-filter-name-test-id'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('variable-filter-operator-test-id'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('variable-filter-value-test-id'),
    ).toBeInTheDocument();
  });

  it('should hide value field when operator does not require value', () => {
    render(
      <VariableFilterRow
        condition={makeCondition({operator: 'exists'})}
        onChange={vi.fn()}
        onDelete={vi.fn()}
        isDeleteHidden={true}
        rowIndex={0}
        {...defaultRowProps}
      />,
    );

    expect(
      screen.queryByTestId('variable-filter-value-test-id'),
    ).not.toBeInTheDocument();
  });

  it('should visually hide delete button when isDeleteHidden is true', () => {
    render(
      <VariableFilterRow
        condition={makeCondition()}
        onChange={vi.fn()}
        onDelete={vi.fn()}
        isDeleteHidden={true}
        rowIndex={0}
        {...defaultRowProps}
      />,
    );

    expect(screen.getByTestId('delete-variable-filter-test-id')).toHaveStyle({
      visibility: 'hidden',
    });
  });

  it('should show delete button when isDeleteHidden is false', () => {
    render(
      <VariableFilterRow
        condition={makeCondition()}
        onChange={vi.fn()}
        onDelete={vi.fn()}
        isDeleteHidden={false}
        rowIndex={0}
        {...defaultRowProps}
      />,
    );

    expect(screen.getByTestId('delete-variable-filter-test-id')).toHaveStyle({
      visibility: 'visible',
    });
  });

  it('should call onChange with updated name when name input changes', async () => {
    const onChange = vi.fn();
    const {user} = render(
      <VariableFilterRow
        condition={makeCondition()}
        onChange={onChange}
        onDelete={vi.fn()}
        isDeleteHidden={true}
        rowIndex={0}
        {...defaultRowProps}
      />,
    );

    await user.type(screen.getByTestId('variable-filter-name-test-id'), 'x');

    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({name: 'x'}));
  });

  it('should call onChange with updated value when value input changes', async () => {
    const onChange = vi.fn();
    const {user} = render(
      <VariableFilterRow
        condition={makeCondition({name: 'status'})}
        onChange={onChange}
        onDelete={vi.fn()}
        isDeleteHidden={true}
        rowIndex={0}
        {...defaultRowProps}
      />,
    );

    await user.type(screen.getByTestId('variable-filter-value-test-id'), 'v');

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({value: 'v'}),
    );
  });

  it('should call onDelete when delete button is clicked', async () => {
    const onDelete = vi.fn();
    const {user} = render(
      <VariableFilterRow
        condition={makeCondition()}
        onChange={vi.fn()}
        onDelete={onDelete}
        isDeleteHidden={false}
        rowIndex={0}
        {...defaultRowProps}
      />,
    );

    await user.click(screen.getByTestId('delete-variable-filter-test-id'));

    expect(onDelete).toHaveBeenCalledTimes(1);
  });

  it('should show name error when name error is provided', () => {
    render(
      <VariableFilterRow
        condition={makeCondition()}
        onChange={vi.fn()}
        onDelete={vi.fn()}
        isDeleteHidden={true}
        rowIndex={0}
        errors={{name: 'Variable name is required'}}
        onBlur={vi.fn()}
      />,
    );

    expect(screen.getByText('Variable name is required')).toBeInTheDocument();
    expect(screen.getByTestId('variable-filter-name-test-id')).toHaveAttribute(
      'aria-invalid',
      'true',
    );
  });

  it('should show value error when value error is provided', () => {
    render(
      <VariableFilterRow
        condition={makeCondition({name: 'status'})}
        onChange={vi.fn()}
        onDelete={vi.fn()}
        isDeleteHidden={true}
        rowIndex={0}
        errors={{value: 'Value must be valid JSON'}}
        onBlur={vi.fn()}
      />,
    );

    expect(screen.getByText('Value must be valid JSON')).toBeInTheDocument();
  });

  it('should call onBlur when name field loses focus', async () => {
    const onBlur = vi.fn();
    const {user} = render(
      <VariableFilterRow
        condition={makeCondition()}
        onChange={vi.fn()}
        onDelete={vi.fn()}
        isDeleteHidden={true}
        rowIndex={0}
        errors={{}}
        onBlur={onBlur}
      />,
    );

    await user.click(screen.getByTestId('variable-filter-name-test-id'));
    await user.tab();

    expect(onBlur).toHaveBeenCalledTimes(1);
  });

  it('should call onBlur when value field loses focus', async () => {
    const onBlur = vi.fn();
    const {user} = render(
      <VariableFilterRow
        condition={makeCondition({name: 'status'})}
        onChange={vi.fn()}
        onDelete={vi.fn()}
        isDeleteHidden={true}
        rowIndex={0}
        errors={{}}
        onBlur={onBlur}
      />,
    );

    await user.click(screen.getByTestId('variable-filter-value-test-id'));
    await user.tab();

    expect(onBlur).toHaveBeenCalledTimes(1);
  });
});
