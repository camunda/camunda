/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {VariableFilter} from '.';
import {variableFilterStore} from 'modules/stores/variableFilter';

beforeEach(() => {
  variableFilterStore.reset();
});

describe('<VariableFilter />', () => {
  it('should show "Add conditions" label when no conditions are set', () => {
    render(<VariableFilter />);

    expect(
      screen.getByRole('button', {name: 'Add conditions'}),
    ).toBeInTheDocument();
  });

  it('should show "Edit conditions" label when conditions exist', () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
    ]);

    render(<VariableFilter />);

    expect(
      screen.getByRole('button', {name: 'Edit conditions'}),
    ).toBeInTheDocument();
  });

  it('should render condition summary labels when conditions exist', () => {
    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
      {name: 'retries', operator: 'exists', value: ''},
    ]);

    render(<VariableFilter />);

    expect(screen.getByText('status equals "active"')).toBeInTheDocument();
    expect(screen.getByText('retries exists')).toBeInTheDocument();
  });

  it('should not render condition list when no conditions are set', () => {
    render(<VariableFilter />);

    expect(
      screen.queryByRole('list', {name: 'Active variable filters'}),
    ).not.toBeInTheDocument();
  });

  it('should open the modal when the button is clicked', async () => {
    const {user} = render(<VariableFilter />);

    await user.click(screen.getByRole('button', {name: 'Add conditions'}));

    expect(
      screen.getByRole('dialog', {name: 'Filter by Variable'}),
    ).toBeInTheDocument();
  });

  it('should update store on apply', async () => {
    const {user} = render(<VariableFilter />);

    await user.click(screen.getByRole('button', {name: 'Add conditions'}));

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
    expect(
      screen.getByRole('button', {name: 'Edit conditions'}),
    ).toBeInTheDocument();
  });

  it('should not update store on cancel', async () => {
    const {user} = render(<VariableFilter />);

    await user.click(screen.getByRole('button', {name: 'Add conditions'}));
    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(variableFilterStore.conditions).toHaveLength(0);
    expect(
      screen.getByRole('button', {name: 'Add conditions'}),
    ).toBeInTheDocument();
  });

  it('should reset draft rows when modal is reopened after adding unsaved rows', async () => {
    const {user} = render(<VariableFilter />);

    await user.click(screen.getByRole('button', {name: 'Add conditions'}));
    await user.click(screen.getByRole('button', {name: 'Add condition'}));
    expect(screen.getAllByPlaceholderText('Variable name')).toHaveLength(2);

    await user.click(screen.getByRole('button', {name: 'Cancel'}));
    await user.click(screen.getByRole('button', {name: 'Add conditions'}));

    expect(screen.getAllByPlaceholderText('Variable name')).toHaveLength(1);
  });
});
