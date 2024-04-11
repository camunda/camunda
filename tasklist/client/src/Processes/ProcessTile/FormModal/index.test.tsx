/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {
  fireEvent,
  render,
  screen,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {FormModal} from '.';
import {createMockProcess} from 'modules/queries/useProcesses';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import * as formMocks from 'modules/mock-schema/mocks/form';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  type Props = {
    children?: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <QueryClientProvider client={mockClient}>
        <MockThemeProvider>{children}</MockThemeProvider>
      </QueryClientProvider>
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

    expect(
      within(screen.getByRole('alert')).getByText('Something went wrong'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('alert')).getByText(
        'Form could not be submitted. Please try again later.',
      ),
    ).toBeInTheDocument();
  });

  it('should handle missing tenant', async () => {
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
        isMultiTenancyEnabled
        tenantId="tenantA"
      />,
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
  });

  it('should hide submission when reopening modal', async () => {
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
        isMultiTenancyEnabled={false}
      />,
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('form-skeleton'),
    );

    expect(screen.queryByText('Something went wrong')).not.toBeInTheDocument();
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
