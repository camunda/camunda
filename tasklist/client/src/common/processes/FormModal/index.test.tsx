/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  fireEvent,
  render,
  screen,
  waitForElementToBeRemoved,
  within,
} from 'common/testing/testing-library';
import {FormModal} from '.';
import * as mockFormSchema from 'common/mocks/form-schema';

describe('<FormModal />', () => {
  it('show a loading skeleton', () => {
    const {rerender} = render(
      <FormModal
        processDisplayName="Process 0"
        schema={null}
        fetchStatus="paused"
        status="pending"
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
    );

    expect(screen.getByTestId('form-skeleton')).toBeVisible();

    rerender(
      <FormModal
        processDisplayName="Process 0"
        schema={null}
        fetchStatus="fetching"
        status="pending"
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
    );

    expect(screen.getByTestId('form-skeleton')).toBeVisible();

    rerender(
      <FormModal
        processDisplayName="Process 0"
        schema={mockFormSchema.basicInputForm}
        fetchStatus="idle"
        status="success"
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
    );

    expect(screen.queryByTestId('form-skeleton')).not.toBeInTheDocument();
  });

  it('should submit a form', async () => {
    vi.useFakeTimers({
      shouldAdvanceTime: true,
    });

    const mockOnSubmit = vi.fn();

    const {user} = render(
      <FormModal
        processDisplayName="Process 0"
        schema={mockFormSchema.basicInputForm}
        fetchStatus="idle"
        status="success"
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={mockOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
    );

    expect(screen.queryByTestId('form-skeleton')).not.toBeInTheDocument();

    await user.click(
      await screen.findByRole('textbox', {name: /my variable/i}),
    );
    await user.type(
      screen.getByRole('textbox', {name: /my variable/i}),
      'var1',
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /is cool\?/i,
      }),
      'Yes',
    );
    fireEvent.click(
      screen.getByRole('button', {
        name: /start process/i,
      }),
    );
    vi.runOnlyPendingTimers();

    expect(
      screen.getByRole('button', {
        name: /start process/i,
      }),
    ).toBeDisabled();
    expect(screen.getByTestId('loading-spinner')).toBeInTheDocument();
    expect(mockOnSubmit).toHaveBeenCalled();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('loading-spinner'),
    );
    vi.useRealTimers();
  });

  it('should handle closing', async () => {
    const mockOnClose = vi.fn();

    render(
      <FormModal
        processDisplayName="Process 0"
        schema={mockFormSchema.basicInputForm}
        fetchStatus="idle"
        status="success"
        isOpen
        onClose={mockOnClose}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
    );

    expect(screen.queryByTestId('form-skeleton')).not.toBeInTheDocument();

    fireEvent.click(
      screen.getByRole('button', {
        name: /cancel/i,
      }),
    );

    expect(mockOnClose).toHaveBeenCalledTimes(1);

    fireEvent.click(
      screen.getByRole('button', {
        name: /close/i,
      }),
    );

    expect(mockOnClose).toHaveBeenCalledTimes(2);
  });

  it('should handle invalid forms', async () => {
    render(
      <FormModal
        processDisplayName="Process 0"
        schema={mockFormSchema.invalidForm}
        fetchStatus="idle"
        status="success"
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
    );

    expect(screen.queryByTestId('form-skeleton')).not.toBeInTheDocument();

    expect(
      await within(await screen.findByRole('alert')).findByText(
        'Something went wrong',
      ),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('alert')).getByText(
        'We were not able to render the form. Please contact your process administrator to fix the form schema.',
      ),
    ).toBeInTheDocument();
  });

  it('should handle form fetching failure', async () => {
    render(
      <FormModal
        processDisplayName="Process 0"
        schema={null}
        fetchStatus="idle"
        status="error"
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
    );

    expect(screen.queryByTestId('form-skeleton')).not.toBeInTheDocument();

    expect(
      within(screen.getByRole('alert')).getByText('Something went wrong'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('alert')).getByText(
        'We were not able to load the form. Please try again or contact your Tasklist administrator.',
      ),
    ).toBeInTheDocument();
  });

  it('should handle submission failure', async () => {
    vi.useFakeTimers({
      shouldAdvanceTime: true,
    });

    const mockOnSubmit = vi.fn(() => {
      throw new Error('Mock error');
    });

    const {user} = render(
      <FormModal
        processDisplayName="Process 0"
        schema={mockFormSchema.basicInputForm}
        fetchStatus="idle"
        status="success"
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={mockOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
    );

    expect(screen.queryByTestId('form-skeleton')).not.toBeInTheDocument();

    await user.type(
      await screen.findByRole('textbox', {name: /my variable/i}),
      'var1',
    );
    vi.runOnlyPendingTimers();
    await user.type(
      screen.getByRole('textbox', {
        name: /is cool\?/i,
      }),
      'Yes',
    );
    vi.runOnlyPendingTimers();
    fireEvent.click(
      screen.getByRole('button', {
        name: /start process/i,
      }),
    );

    expect(
      within(screen.getByRole('alert')).getByText('Something went wrong'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('alert')).getByText(
        'Form could not be submitted. Please try again later.',
      ),
    ).toBeInTheDocument();
    vi.useRealTimers();
  });

  it('should handle missing tenant', async () => {
    vi.useFakeTimers({
      shouldAdvanceTime: true,
    });

    const mockFailOnSubmit = vi.fn(() => {
      throw new Error('Mock error');
    });
    const mockSuccessOnSubmit = vi.fn();

    const {user, rerender} = render(
      <FormModal
        processDisplayName="Process 0"
        schema={mockFormSchema.basicInputForm}
        fetchStatus="idle"
        status="success"
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={mockFailOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled
        tenantId={undefined}
      />,
    );

    expect(screen.queryByTestId('form-skeleton')).not.toBeInTheDocument();

    await user.click(
      await screen.findByRole('textbox', {name: /my variable/i}),
    );
    await user.type(
      screen.getByRole('textbox', {name: /my variable/i}),
      'var1',
    );
    vi.runOnlyPendingTimers();
    await user.type(
      screen.getByRole('textbox', {
        name: /is cool\?/i,
      }),
      'Yes',
    );
    vi.runOnlyPendingTimers();
    fireEvent.click(
      screen.getByRole('button', {
        name: /start process/i,
      }),
    );

    expect(
      within(screen.getByRole('alert')).getByText('Something went wrong'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('alert')).getByText(
        'You must first select a tenant to start a process.',
      ),
    ).toBeInTheDocument();

    rerender(
      <FormModal
        processDisplayName="Process 0"
        schema={mockFormSchema.basicInputForm}
        fetchStatus="idle"
        status="success"
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={mockSuccessOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled
        tenantId="tenantA"
      />,
    );

    await user.type(
      await screen.findByRole('textbox', {name: /my variable/i}),
      'var1',
    );
    vi.runOnlyPendingTimers();
    await user.type(
      screen.getByRole('textbox', {
        name: /is cool\?/i,
      }),
      'Yes',
    );
    vi.runOnlyPendingTimers();
    fireEvent.click(
      screen.getByRole('button', {
        name: /start process/i,
      }),
    );

    expect(
      screen.getByRole('button', {
        name: /start process/i,
      }),
    ).toBeDisabled();
    expect(screen.getByTestId('loading-spinner')).toBeInTheDocument();
    expect(mockSuccessOnSubmit).toHaveBeenCalled();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('loading-spinner'),
    );
    vi.useRealTimers();
  });

  it('should hide submission when reopening modal', async () => {
    vi.useFakeTimers({
      shouldAdvanceTime: true,
    });

    const mockOnSubmit = vi.fn(() => {
      throw new Error('Mock error');
    });

    const {rerender, user} = render(
      <FormModal
        processDisplayName="Process 0"
        schema={mockFormSchema.basicInputForm}
        fetchStatus="idle"
        status="success"
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={mockOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
    );

    expect(screen.queryByTestId('form-skeleton')).not.toBeInTheDocument();

    await user.type(
      await screen.findByRole('textbox', {name: /my variable/i}),
      'var1',
    );
    vi.runOnlyPendingTimers();
    await user.type(
      screen.getByRole('textbox', {
        name: /is cool\?/i,
      }),
      'Yes',
    );
    vi.runOnlyPendingTimers();
    fireEvent.click(
      screen.getByRole('button', {
        name: /start process/i,
      }),
    );

    expect(
      await within(screen.getByRole('alert')).findByText(
        'Something went wrong',
      ),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('alert')).getByText(
        'Form could not be submitted. Please try again later.',
      ),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: /close/i}));

    rerender(
      <FormModal
        processDisplayName="Process 0"
        schema={null}
        fetchStatus="idle"
        status="pending"
        isOpen={false}
        onClose={() => Promise.resolve()}
        onSubmit={mockOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
    );

    // we need to check the class because Carbon never stops rendering the dialog
    expect(screen.getByRole('dialog')).not.toHaveClass('is-visible');

    rerender(
      <FormModal
        processDisplayName="Process 0"
        schema={mockFormSchema.basicInputForm}
        fetchStatus="idle"
        status="success"
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={mockOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
    );

    expect(screen.queryByTestId('form-skeleton')).not.toBeInTheDocument();

    expect(screen.queryByText('Something went wrong')).not.toBeInTheDocument();
    vi.useRealTimers();
  });

  it('should copy the form link when clicking on share', async () => {
    const {user} = render(
      <FormModal
        processDisplayName="Process 0"
        schema={mockFormSchema.basicInputForm}
        fetchStatus="idle"
        status="success"
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
    );

    expect(screen.queryByTestId('form-skeleton')).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {
        name: 'Share process URL',
      }),
    );

    expect(await navigator.clipboard.readText()).toBe('http://localhost:3000/');
  });
});
