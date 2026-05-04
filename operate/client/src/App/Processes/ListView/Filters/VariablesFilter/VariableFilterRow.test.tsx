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

const renderRow = (
  condition: Partial<DraftCondition> = {},
  props: {onDelete?: () => void; isDeleteHidden?: boolean} = {},
) => {
  const draft: DraftCondition = {
    id: 'test-id',
    name: '',
    operator: 'equals',
    value: '',
    ...condition,
  };

  return render(
    <Form<RowFormValues>
      onSubmit={vi.fn()}
      initialValues={{conditions: [draft]}}
      mutators={{...arrayMutators}}
    >
      {() => (
        <FieldArray name="conditions">
          {({fields}) =>
            fields.map((fieldName, index) => (
              <VariableFilterRow
                key={fieldName}
                fieldName={fieldName}
                rowIndex={index}
                onDelete={props.onDelete ?? vi.fn()}
                isDeleteHidden={props.isDeleteHidden ?? true}
              />
            ))
          }
        </FieldArray>
      )}
    </Form>,
  );
};

describe('<VariableFilterRow />', () => {
  it('should render name and value inputs and operator dropdown', () => {
    renderRow();

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
    renderRow({operator: 'exists'});

    expect(
      screen.queryByTestId('variable-filter-value-test-id'),
    ).not.toBeInTheDocument();
  });

  it('should visually hide delete button when isDeleteHidden is true', () => {
    renderRow({}, {isDeleteHidden: true});

    expect(screen.getByTestId('delete-variable-filter-test-id')).toHaveStyle({
      visibility: 'hidden',
    });
  });

  it('should show delete button when isDeleteHidden is false', () => {
    renderRow({}, {isDeleteHidden: false});

    expect(screen.getByTestId('delete-variable-filter-test-id')).toHaveStyle({
      visibility: 'visible',
    });
  });

  it('should update name input when user types', async () => {
    const {user} = renderRow();

    await user.type(screen.getByTestId('variable-filter-name-test-id'), 'x');

    expect(screen.getByTestId('variable-filter-name-test-id')).toHaveValue('x');
  });

  it('should update value input when user types', async () => {
    const {user} = renderRow({name: 'status'});

    await user.type(screen.getByTestId('variable-filter-value-test-id'), 'v');

    expect(screen.getByTestId('variable-filter-value-test-id')).toHaveValue(
      'v',
    );
  });

  it('should call onDelete when delete button is clicked', async () => {
    const onDelete = vi.fn();
    const {user} = renderRow({}, {onDelete, isDeleteHidden: false});

    await user.click(screen.getByTestId('delete-variable-filter-test-id'));

    expect(onDelete).toHaveBeenCalledTimes(1);
  });

  it('should hide value field after switching to exists operator', async () => {
    const {user} = renderRow();

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('exists'));

    expect(
      screen.queryByTestId('variable-filter-value-test-id'),
    ).not.toBeInTheDocument();
  });

  it('should show value field after switching back from exists to equals', async () => {
    const {user} = renderRow({operator: 'exists'});

    await user.click(screen.getByRole('combobox', {name: 'Operator'}));
    await user.click(screen.getByText('equals'));

    expect(
      screen.getByTestId('variable-filter-value-test-id'),
    ).toBeInTheDocument();
  });
});
