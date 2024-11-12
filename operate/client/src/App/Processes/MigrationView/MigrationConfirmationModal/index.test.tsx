/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MigrationConfirmationModal} from '.';
import {render, screen} from 'modules/testing-library';

describe('MigrationConfirmationModal', () => {
  it('should render migration details', () => {
    render(
      <MigrationConfirmationModal
        open={true}
        setOpen={jest.fn()}
        onSubmit={jest.fn()}
      />,
    );

    // This just expects MigrationDetails component to be rendered.
    // A more detailed test can be found in MigrationDetails/index.test.tsx
    expect(screen.getByText(/You are about to migrate/i)).toBeInTheDocument();
  });

  it('should call setOpen(false)', async () => {
    const setOpenMock = jest.fn();
    const onSubmitMock = jest.fn();

    const {user} = render(
      <MigrationConfirmationModal
        open={true}
        setOpen={setOpenMock}
        onSubmit={onSubmitMock}
      />,
    );

    await user.click(screen.getByRole('button', {name: /close/i}));
    await user.click(screen.getByRole('button', {name: /cancel/i}));

    expect(setOpenMock).toHaveBeenCalledTimes(2);
    expect(setOpenMock).toHaveBeenNthCalledWith(1, false);
    expect(setOpenMock).toHaveBeenNthCalledWith(2, false);
    expect(onSubmitMock).not.toHaveBeenCalled();

    setOpenMock.mockClear();
    onSubmitMock.mockClear();
  });

  it('should confirm button click', async () => {
    const onSubmitMock = jest.fn();

    const {user} = render(
      <MigrationConfirmationModal
        open={true}
        setOpen={jest.fn()}
        onSubmit={onSubmitMock}
      />,
    );

    expect(screen.getByRole('button', {name: /confirm/i})).toBeDisabled();

    await user.type(screen.getByRole('textbox'), 'MIGRATE');

    expect(screen.getByRole('button', {name: /confirm/i})).toBeEnabled();
    screen.getByRole('button', {name: /confirm/i}).click();

    expect(onSubmitMock).toHaveBeenCalledTimes(1);

    onSubmitMock.mockClear();
  });

  it('should submit on enter', async () => {
    const onSubmitMock = jest.fn();

    const {user} = render(
      <MigrationConfirmationModal
        open={true}
        setOpen={jest.fn()}
        onSubmit={onSubmitMock}
      />,
    );

    expect(screen.getByRole('button', {name: /confirm/i})).toBeDisabled();

    await user.keyboard('[Enter]');
    expect(onSubmitMock).not.toHaveBeenCalled();

    await user.type(screen.getByRole('textbox'), 'MIGRATE');
    expect(screen.getByRole('button', {name: /confirm/i})).toBeEnabled();

    await user.keyboard('[Enter]');
    expect(onSubmitMock).toHaveBeenCalledTimes(1);

    onSubmitMock.mockClear();
  });

  it('should auto focus', () => {
    render(
      <MigrationConfirmationModal
        open={true}
        setOpen={jest.fn()}
        onSubmit={jest.fn()}
      />,
    );

    expect(screen.getByRole('textbox')).toHaveFocus();
  });

  it('should validate input', async () => {
    const errorMessage = /Value must match MIGRATE/i;

    const {user} = render(
      <MigrationConfirmationModal
        open={true}
        setOpen={jest.fn()}
        onSubmit={jest.fn()}
      />,
    );

    expect(screen.queryByText(errorMessage)).not.toBeInTheDocument();

    await user.type(screen.getByRole('textbox'), 'Foo');
    expect(screen.getByText(errorMessage)).toBeInTheDocument();

    await user.clear(screen.getByRole('textbox'));
    await user.type(screen.getByRole('textbox'), 'MIGRATE');
    expect(screen.queryByText(errorMessage)).not.toBeInTheDocument();
  });
});
