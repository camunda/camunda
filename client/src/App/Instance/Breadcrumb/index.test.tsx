/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Breadcrumb} from './index';
import {createInstance} from 'modules/testUtils';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {LocationLog} from 'modules/utils/LocationLog';
import {mockServer} from 'modules/mock-server/node';
import {rest} from 'msw';
import {currentInstanceStore} from 'modules/stores/currentInstance';

const createWrapper = (initialPath: string = '/processes/123') => {
  const Wrapper: React.FC = ({children}) => (
    <ThemeProvider>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/processes/:processInstanceId" element={children} />
        </Routes>
        <LocationLog />
      </MemoryRouter>
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
            ...createInstance(),
            id: '123',
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

    render(<Breadcrumb />, {
      wrapper: createWrapper('/processes/123'),
    });

    expect(screen.getByTestId('pathname')).toHaveTextContent('/processes/123');

    userEvent.click(
      screen.getByRole('link', {
        name: /View Process Parent Process Name - Instance 546546543276/,
      })
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/processes/546546543276'
    );

    userEvent.click(
      screen.getByRole('link', {
        name: /View Process 1st level Child Process Name - Instance 968765314354/,
      })
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/processes/968765314354'
    );

    userEvent.click(
      screen.getByRole('link', {
        name: /View Process 2nd level Child Process Name - Instance 2251799813685447/,
      })
    );
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/processes/2251799813685447'
    );
  });
});
