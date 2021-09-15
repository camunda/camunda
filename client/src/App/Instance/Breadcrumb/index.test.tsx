/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {ThemeProvider} from 'modules/theme/ThemeProvider';

import {Breadcrumb} from './index';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {createMemoryHistory} from 'history';
import {Router, Route} from 'react-router-dom';
import {currentInstanceStore} from 'modules/stores/currentInstance';

const createWrapper = (
  historyMock = createMemoryHistory({
    initialEntries: ['/instances/123'],
  })
) => {
  const Wrapper: React.FC = ({children}) => (
    <ThemeProvider>
      <Router history={historyMock}>
        <Route path="/instances/:processInstanceId">{children}</Route>
      </Router>
    </ThemeProvider>
  );

  return Wrapper;
};

describe('User', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get(`/api/process-instances/:id`, (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 123,
            state: 'ACTIVE',
            processName: 'Base instance name',
            callHierarchy: [
              {
                instanceId: '546546543276',
                processDefinitionName: 'Parent Process Name',
              },
              {
                instanceId: '968765314354',
                processDefinitionName: '1st level Child Process Name',
              },
              {
                instanceId: '2251799813685447',
                processDefinitionName: '2nd level Child Process Name',
              },
            ],
          })
        )
      )
    );
  });

  afterEach(() => {
    currentInstanceStore.reset();
  });

  it('should render breadcrumb', async () => {
    await currentInstanceStore.fetchCurrentInstance();

    render(<Breadcrumb />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText('Parent Process Name')).toBeInTheDocument();
    expect(
      screen.getByText('1st level Child Process Name')
    ).toBeInTheDocument();
    expect(
      screen.getByText('2nd level Child Process Name')
    ).toBeInTheDocument();
    expect(screen.getByText('Base instance name')).toBeInTheDocument();
  });

  it('should navigate to instance detail on click', async () => {
    await currentInstanceStore.fetchCurrentInstance();

    const MOCK_HISTORY = createMemoryHistory({
      initialEntries: ['/instances/123'],
    });
    render(<Breadcrumb />, {
      wrapper: createWrapper(MOCK_HISTORY),
    });

    expect(MOCK_HISTORY.location.pathname).toBe('/instances/123');

    userEvent.click(
      screen.getByRole('link', {
        name: /View Process Parent Process Name - Instance 546546543276/,
      })
    );
    expect(MOCK_HISTORY.location.pathname).toBe('/instances/546546543276');

    userEvent.click(
      screen.getByRole('link', {
        name: /View Process 1st level Child Process Name - Instance 968765314354/,
      })
    );
    expect(MOCK_HISTORY.location.pathname).toBe('/instances/968765314354');

    userEvent.click(
      screen.getByRole('link', {
        name: /View Process 2nd level Child Process Name - Instance 2251799813685447/,
      })
    );
    expect(MOCK_HISTORY.location.pathname).toBe('/instances/2251799813685447');
  });
});
