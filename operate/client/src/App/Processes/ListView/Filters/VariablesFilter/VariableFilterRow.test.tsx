/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {Form} from 'react-final-form';
import arrayMutators from 'final-form-arrays';
import {FieldArray} from 'react-final-form-arrays';
import {VariableFilterRow} from './VariableFilterRow';
import type {DraftCondition} from './constants';

type RowFormValues = {conditions: DraftCondition[]};

const getWrapper = (condition: Partial<DraftCondition> = {}) => {
  const draft: DraftCondition = {
    name: '',
    operator: 'equals',
    value: '',
    ...condition,
  };

  const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
    <Form<RowFormValues>
      onSubmit={vi.fn()}
      initialValues={{conditions: [draft]}}
      mutators={{...arrayMutators}}
    >
      {() => <FieldArray name="conditions">{() => children}</FieldArray>}
    </Form>
  );

  return Wrapper;
};

describe('<VariableFilterRow />', () => {
  it('should render name and value inputs and operator dropdown', () => {
    render(
      <VariableFilterRow
        fieldName="conditions[0]"
        rowIndex={0}
        onDelete={vi.fn()}
        onEditValue={vi.fn()}
        isDeleteHidden
      />,
      {wrapper: getWrapper()},
    );

    expect(screen.getByTestId('variable-filter-name-0')).toBeInTheDocument();
    expect(
      screen.getByTestId('variable-filter-operator-0'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('variable-filter-value-0')).toBeInTheDocument();
  });

  it('should hide value field when operator does not require value', () => {
    render(
      <VariableFilterRow
        fieldName="conditions[0]"
        rowIndex={0}
        onDelete={vi.fn()}
        onEditValue={vi.fn()}
        isDeleteHidden
      />,
      {wrapper: getWrapper({operator: 'exists'})},
    );

    expect(
      screen.queryByTestId('variable-filter-value-0'),
    ).not.toBeInTheDocument();
  });

  it('should visually hide delete button when isDeleteHidden is true', () => {
    render(
      <VariableFilterRow
        fieldName="conditions[0]"
        rowIndex={0}
        onDelete={vi.fn()}
        onEditValue={vi.fn()}
        isDeleteHidden
      />,
      {wrapper: getWrapper()},
    );
    expect(screen.getByTestId('delete-variable-filter-0')).not.toBeVisible();
  });

  it('should show delete button when isDeleteHidden is false', () => {
    render(
      <VariableFilterRow
        fieldName="conditions[0]"
        rowIndex={0}
        onDelete={vi.fn()}
        onEditValue={vi.fn()}
        isDeleteHidden={false}
      />,
      {wrapper: getWrapper()},
    );

    expect(screen.getByTestId('delete-variable-filter-0')).toBeVisible();
  });

  it('should update name input when user types', async () => {
    const {user} = render(
      <VariableFilterRow
        fieldName="conditions[0]"
        rowIndex={0}
        onDelete={vi.fn()}
        onEditValue={vi.fn()}
        isDeleteHidden
      />,
      {wrapper: getWrapper()},
    );

    await user.type(screen.getByTestId('variable-filter-name-0'), 'x');

    expect(screen.getByTestId('variable-filter-name-0')).toHaveValue('x');
  });

  it('should update value input when user types', async () => {
    const {user} = render(
      <VariableFilterRow
        fieldName="conditions[0]"
        rowIndex={0}
        onDelete={vi.fn()}
        onEditValue={vi.fn()}
        isDeleteHidden
      />,
      {wrapper: getWrapper({name: 'status'})},
    );

    await user.type(screen.getByTestId('variable-filter-value-0'), 'v');

    expect(screen.getByTestId('variable-filter-value-0')).toHaveValue('v');
  });

  it('should call onDelete when delete button is clicked', async () => {
    const onDelete = vi.fn();
    const {user} = render(
      <VariableFilterRow
        fieldName="conditions[0]"
        rowIndex={0}
        onDelete={onDelete}
        onEditValue={vi.fn()}
        isDeleteHidden={false}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByTestId('delete-variable-filter-0'));

    expect(onDelete).toHaveBeenCalledTimes(1);
  });

  it('should hide value field after switching to exists operator', async () => {
    const {user} = render(
      <VariableFilterRow
        fieldName="conditions[0]"
        rowIndex={0}
        onDelete={vi.fn()}
        onEditValue={vi.fn()}
        isDeleteHidden
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('exists'));

    expect(
      screen.queryByTestId('variable-filter-value-0'),
    ).not.toBeInTheDocument();
  });

  it('should show value field after switching back from exists to equals', async () => {
    const {user} = render(
      <VariableFilterRow
        fieldName="conditions[0]"
        rowIndex={0}
        onDelete={vi.fn()}
        onEditValue={vi.fn()}
        isDeleteHidden
      />,
      {wrapper: getWrapper({operator: 'exists'})},
    );

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('equals'));

    expect(screen.getByTestId('variable-filter-value-0')).toBeInTheDocument();
  });

  it('should call onEditValue with rowIndex when Maximize icon is clicked', async () => {
    const onEditValue = vi.fn();
    const {user} = render(
      <VariableFilterRow
        fieldName="conditions[0]"
        rowIndex={0}
        onDelete={vi.fn()}
        onEditValue={onEditValue}
        isDeleteHidden
      />,
      {wrapper: getWrapper({name: 'status', value: '"active"'})},
    );

    await user.click(screen.getByRole('button', {name: 'Open JSON editor'}));

    expect(onEditValue).toHaveBeenCalledWith(0);
  });
});
