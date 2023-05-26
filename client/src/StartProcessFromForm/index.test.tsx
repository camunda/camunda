/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {StartProcessFromForm} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {ReactQueryProvider} from 'modules/ReactQueryProvider';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {rest} from 'msw';
import * as formMocks from 'modules/mock-schema/mocks/form';
import {notificationsStore} from 'modules/stores/notifications';

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

const getWrapper = ({
  initialEntries,
}: Pick<React.ComponentProps<typeof MemoryRouter>, 'initialEntries'>) => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <ReactQueryProvider>
      <MockThemeProvider>
        <MemoryRouter initialEntries={initialEntries}>
          <Routes>
            <Route path="/new/:bpmnProcessId" element={children} />
          </Routes>
        </MemoryRouter>
      </MockThemeProvider>
    </ReactQueryProvider>
  );

  return Wrapper;
};

describe('<StartProcessFromForm />', () => {
  it('should render a form', async () => {
    nodeMockServer.use(
      rest.get('/v1/external/process/:bpmnProcessId/form', (_, res, ctx) =>
        res(ctx.json(formMocks.form)),
      ),
    );

    render(<StartProcessFromForm />, {
      wrapper: getWrapper({
        initialEntries: ['/new/foo'],
      }),
    });

    await waitForElementToBeRemoved(screen.getByTestId('public-form-skeleton'));

    expect(screen.getByText('A sample text')).toBeInTheDocument();
  });

  it('should a request error message', async () => {
    nodeMockServer.use(
      rest.get('/v1/external/process/:bpmnProcessId/form', (_, res, ctx) =>
        res(ctx.status(500)),
      ),
    );

    render(<StartProcessFromForm />, {
      wrapper: getWrapper({
        initialEntries: ['/new/foo'],
      }),
    });

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Could not fetch form',
        isDismissable: false,
      }),
    );
  });

  it('should a bad form schema error message', async () => {
    nodeMockServer.use(
      rest.get('/v1/external/process/:bpmnProcessId/form', (_, res, ctx) =>
        res(ctx.json(formMocks.invalidForm)),
      ),
    );

    render(<StartProcessFromForm />, {
      wrapper: getWrapper({
        initialEntries: ['/new/foo'],
      }),
    });

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Could not render form',
        isDismissable: false,
      }),
    );
  });
});
