/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing/testing-library';
import {Form} from 'react-final-form';
import {AdvancedStringFilter} from '.';

type SubmittedValues = {businessId?: string};

const getWrapper = ({
  onSubmit = vi.fn(),
  initialValues,
}: {
  onSubmit?: (values: SubmittedValues) => void;
  initialValues?: SubmittedValues;
} = {}) => {
  const Wrapper = ({children}: {children: React.ReactNode}) => (
    <Form<SubmittedValues> onSubmit={onSubmit} initialValues={initialValues}>
      {({handleSubmit}) => (
        <form onSubmit={handleSubmit}>
          {children}
          <button type="submit">submit</button>
        </form>
      )}
    </Form>
  );
  return Wrapper;
};

describe('<AdvancedStringFilter />', () => {
  it('renders dropdown and input with the first operator selected by default', () => {
    render(
      <AdvancedStringFilter
        name="businessId"
        label="Business ID"
        selectableOperators={['$eq', '$like', '$in']}
      />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.getByRole('combobox', {name: 'Business ID filter type'}),
    ).toHaveTextContent('equals');
    expect(screen.getByLabelText('Business ID')).toBeInTheDocument();
  });

  it('decodes the initial form value into operator + value', () => {
    render(
      <AdvancedStringFilter
        name="businessId"
        label="Business ID"
        selectableOperators={['$eq', '$like', '$in']}
      />,
      {wrapper: getWrapper({initialValues: {businessId: 'like_order-123'}})},
    );

    expect(screen.getByRole('combobox')).toHaveTextContent('contains');
    expect(screen.getByLabelText('Business ID')).toHaveValue('order-123');
  });

  it('encodes the form value with the current operator when the user types', async () => {
    const onSubmit = vi.fn();
    const {user} = render(
      <AdvancedStringFilter
        name="businessId"
        label="Business ID"
        selectableOperators={['$eq', '$like', '$in']}
      />,
      {wrapper: getWrapper({onSubmit})},
    );

    await user.type(screen.getByLabelText('Business ID'), 'abc');
    await user.click(screen.getByRole('button', {name: 'submit'}));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({businessId: 'eq_abc'}),
      expect.anything(),
      expect.anything(),
    );
  });

  it('re-encodes the form value when the operator changes', async () => {
    const onSubmit = vi.fn();
    const {user} = render(
      <AdvancedStringFilter
        name="businessId"
        label="Business ID"
        selectableOperators={['$eq', '$like', '$in']}
      />,
      {
        wrapper: getWrapper({
          onSubmit,
          initialValues: {businessId: 'eq_abc'},
        }),
      },
    );

    await user.click(screen.getByRole('combobox'));
    await user.click(screen.getByRole('option', {name: 'contains'}));
    await user.click(screen.getByRole('button', {name: 'submit'}));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({businessId: 'like_abc'}),
      expect.anything(),
      expect.anything(),
    );
  });

  it('clears the form value to undefined when the input is emptied', async () => {
    const onSubmit = vi.fn();
    const {user} = render(
      <AdvancedStringFilter
        name="businessId"
        label="Business ID"
        selectableOperators={['$eq', '$like', '$in']}
      />,
      {
        wrapper: getWrapper({
          onSubmit,
          initialValues: {businessId: 'eq_abc'},
        }),
      },
    );

    await user.clear(screen.getByLabelText('Business ID'));
    await user.click(screen.getByRole('button', {name: 'submit'}));

    expect(onSubmit).toHaveBeenLastCalledWith(
      {},
      expect.anything(),
      expect.anything(),
    );
  });

  it('preserves the dropdown selection when the input is emptied', async () => {
    const {user} = render(
      <AdvancedStringFilter
        name="businessId"
        label="Business ID"
        selectableOperators={['$eq', '$like', '$in']}
      />,
      {wrapper: getWrapper({initialValues: {businessId: 'like_order-123'}})},
    );

    await user.clear(screen.getByLabelText('Business ID'));

    expect(screen.getByRole('combobox')).toHaveTextContent('contains');
  });

  it('preserves the dropdown selection across operator-only changes with an empty value', async () => {
    const {user} = render(
      <AdvancedStringFilter
        name="businessId"
        label="Business ID"
        selectableOperators={['$eq', '$like', '$in']}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('combobox'));
    await user.click(screen.getByRole('option', {name: 'is one of'}));

    expect(screen.getByRole('combobox')).toHaveTextContent('is one of');
  });

  it('treats a malformed value as an empty input', () => {
    render(
      <AdvancedStringFilter
        name="businessId"
        label="Business ID"
        selectableOperators={['$eq', '$like', '$in']}
      />,
      {
        wrapper: getWrapper({
          initialValues: {businessId: 'plain-legacy-value'},
        }),
      },
    );

    expect(screen.getByLabelText('Business ID')).toHaveValue('');
    expect(screen.getByRole('combobox')).toHaveTextContent('equals');
  });
});
