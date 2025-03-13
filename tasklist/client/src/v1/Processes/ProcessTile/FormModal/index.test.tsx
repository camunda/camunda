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
import {createMockProcess} from 'v1/api/useProcesses.query';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {http, HttpResponse} from 'msw';
import * as formMocks from 'v1/mocks/form';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  type Props = {
    children?: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <QueryClientProvider client={mockClient}>{children}</QueryClientProvider>
    );
  };

  return Wrapper;
};

describe('<FormModal />', () => {
  it('should submit a form', async () => {
    vi.useFakeTimers({
      shouldAdvanceTime: true,
    });
    nodeMockServer.use(
      http.get('/v1/forms/:formId', () => {
        return HttpResponse.json(formMocks.form);
      }),
    );

    const mockOnSubmit = vi.fn();

    const {user} = render(
      <FormModal
        process={createMockProcess('process-0')}
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={mockOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('form-skeleton'),
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
    nodeMockServer.use(
      http.get('/v1/forms/:formId', () => {
        return HttpResponse.json(formMocks.form);
      }),
    );

    const mockOnClose = vi.fn();

    render(
      <FormModal
        process={createMockProcess('process-0')}
        isOpen
        onClose={mockOnClose}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('form-skeleton'),
    );

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
    nodeMockServer.use(
      http.get('/v1/forms/:formId', () => {
        return HttpResponse.json(formMocks.invalidForm);
      }),
    );

    render(
      <FormModal
        process={createMockProcess('process-0')}
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('form-skeleton'),
    );

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
    nodeMockServer.use(
      http.get('/v1/forms/:formId', () => {
        return new HttpResponse(null, {status: 500});
      }),
    );

    render(
      <FormModal
        process={createMockProcess('process-0')}
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('form-skeleton'),
    );

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
    nodeMockServer.use(
      http.get('/v1/forms/:formId', () => {
        return HttpResponse.json(formMocks.form);
      }),
    );

    const mockOnSubmit = vi.fn(() => {
      throw new Error('Mock error');
    });

    const {user} = render(
      <FormModal
        process={createMockProcess('process-0')}
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={mockOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('form-skeleton'),
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
        'Form could not be submitted. Please try again later.',
      ),
    ).toBeInTheDocument();
    vi.useRealTimers();
  });

  it('should handle missing tenant', async () => {
    vi.useFakeTimers({
      shouldAdvanceTime: true,
    });
    nodeMockServer.use(
      http.get('/v1/forms/:formId', () => {
        return HttpResponse.json(formMocks.form);
      }),
    );

    const mockFailOnSubmit = vi.fn(() => {
      throw new Error('Mock error');
    });
    const mockSuccessOnSubmit = vi.fn();

    const {user, rerender} = render(
      <FormModal
        process={createMockProcess('process-0')}
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={mockFailOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled
        tenantId={undefined}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('form-skeleton'),
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
        process={createMockProcess('process-0')}
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={mockSuccessOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled
        tenantId="tenantA"
      />,
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
    nodeMockServer.use(
      http.get('/v1/forms/:formId', () => {
        return HttpResponse.json(formMocks.form);
      }),
    );

    const mockOnSubmit = vi.fn(() => {
      throw new Error('Mock error');
    });

    const {rerender, user} = render(
      <FormModal
        process={createMockProcess('process-0')}
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={mockOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('form-skeleton'),
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
        process={createMockProcess('process-0')}
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
        process={createMockProcess('process-0')}
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={mockOnSubmit}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('form-skeleton'),
    );

    expect(screen.queryByText('Something went wrong')).not.toBeInTheDocument();
    vi.useRealTimers();
  });

  it('should copy the form link when clicking on share', async () => {
    nodeMockServer.use(
      http.get('/v1/forms/:formId', () => {
        return HttpResponse.json(formMocks.form);
      }),
    );

    const {user} = render(
      <FormModal
        process={createMockProcess('process-0')}
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={() => Promise.resolve()}
        onFileUpload={() => Promise.resolve(new Map())}
        isMultiTenancyEnabled={false}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('form-skeleton'),
    );

    await user.click(
      screen.getByRole('button', {
        name: 'Share process URL',
      }),
    );

    expect(await navigator.clipboard.readText()).toBe('http://localhost:3000/');
  });
});
