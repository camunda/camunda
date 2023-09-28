/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {rest} from 'msw';
import * as formMocks from 'modules/mock-schema/mocks/form';
import {ReactQueryProvider} from 'modules/ReactQueryProvider';
import {MockThemeProvider} from 'modules/theme/MockProvider';

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ReactQueryProvider>
      <MockThemeProvider>{children}</MockThemeProvider>
    </ReactQueryProvider>
  );
};

describe('<FormModal />', () => {
  it('should submit a form', async () => {
    nodeMockServer.use(
      rest.get('/v1/forms/:formId', (_, res, ctx) => {
        return res(ctx.json(formMocks.form));
      }),
    );

    const mockOnSubmit = jest.fn();

    const {user} = render(
      <FormModal
        process={createMockProcess('process-0')}
        isOpen
        onClose={() => Promise.resolve()}
        onSubmit={mockOnSubmit}
        isMultiTenancyEnabled={false}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(screen.getByTestId('form-skeleton'));

    await user.type(
      screen.getByRole('textbox', {name: /my variable \*/i}),
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
    expect(mockOnSubmit).toHaveBeenCalled();

    await waitForElementToBeRemoved(screen.getByTestId('loading-spinner'));
  });

  it('should handle closing', async () => {
    nodeMockServer.use(
      rest.get('/v1/forms/:formId', (_, res, ctx) => {
        return res(ctx.json(formMocks.form));
      }),
    );

    const mockOnClose = jest.fn();

    render(
      <FormModal
        process={createMockProcess('process-0')}
        isOpen
        onClose={mockOnClose}
        onSubmit={() => Promise.resolve()}
        isMultiTenancyEnabled={false}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(screen.getByTestId('form-skeleton'));

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
      rest.get('/v1/forms/:formId', (_, res, ctx) => {
        return res(ctx.json(formMocks.invalidForm));
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
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(screen.getByTestId('form-skeleton'));

    expect(
      within(screen.getByRole('alert')).getByText('Something went wrong'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('alert')).getByText(
        'We were not able to render the form. Please contact your process administrator to fix the form schema.',
      ),
    ).toBeInTheDocument();
  });

  it('should handle form fetching failure', async () => {
    nodeMockServer.use(
      rest.get('/v1/forms/:formId', (_, res, ctx) => {
        return res(ctx.status(500));
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
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(screen.getByTestId('form-skeleton'));

    expect(
      within(screen.getByRole('alert')).getByText('Something went wrong'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('alert')).getByText(
        'We were not able to load the form. Please check your connection and try again later.',
      ),
    ).toBeInTheDocument();
  });

  it('should handle submission failure', async () => {
    nodeMockServer.use(
      rest.get('/v1/forms/:formId', (_, res, ctx) => {
        return res(ctx.json(formMocks.form));
      }),
    );

    const mockOnSubmit = jest.fn(() => {
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
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(screen.getByTestId('form-skeleton'));

    await user.type(
      screen.getByRole('textbox', {name: /my variable \*/i}),
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
      rest.get('/v1/forms/:formId', (_, res, ctx) => {
        return res(ctx.json(formMocks.form));
      }),
    );

    const mockFailOnSubmit = jest.fn(() => {
      throw new Error('Mock error');
    });
    const mockSuccessOnSubmit = jest.fn();

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
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(screen.getByTestId('form-skeleton'));

    await user.type(
      screen.getByRole('textbox', {name: /my variable \*/i}),
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
      screen.getByRole('textbox', {name: /my variable \*/i}),
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

    await waitForElementToBeRemoved(screen.getByTestId('loading-spinner'));
  });
});
