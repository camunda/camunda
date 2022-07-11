/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessInstances,
} from 'modules/testUtils';
import {DiagramPanel} from './index';
import {processesStore} from 'modules/stores/processes';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {processDiagramStore} from 'modules/stores/processDiagram';

jest.mock('modules/utils/bpmn');

function getWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <MemoryRouter initialEntries={[initialPath]}>
        <ThemeProvider>{children}</ThemeProvider>
      </MemoryRouter>
    );
  };

  return Wrapper;
}

describe('DiagramPanel', () => {
  beforeEach(() => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/processes/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedProcessesMock))
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      )
    );

    processesStore.fetchProcesses();
  });

  afterEach(() => {
    processDiagramStore.reset();
    processesStore.reset();
  });

  it('should render header', async () => {
    render(<DiagramPanel />, {
      wrapper: getWrapper('/processes?process=bigVarProcess&version=1'),
    });

    expect(await screen.findByText('Big variable process')).toBeInTheDocument();
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
  });

  it('should show the loading indicator, when diagram is loading', async () => {
    render(<DiagramPanel />, {
      wrapper: getWrapper('/processes?process=bigVarProcess&version=1'),
    });

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('diagram-spinner'));
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
  });

  it('should show an empty state message when no process is selected', async () => {
    render(<DiagramPanel />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.getByText('There is no Process selected')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see a Diagram, select a Process in the Filters panel'
      )
    ).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should show a message when no process version is selected', async () => {
    render(<DiagramPanel />, {
      wrapper: getWrapper('/processes?process=bigVarProcess&version=all'),
    });

    expect(
      await screen.findByText(
        'There is more than one Version selected for Process "Big variable process"'
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText('To see a Diagram, select a single Version')
    ).toBeInTheDocument();

    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should display bpmnProcessId as process name in the message when no process version is selected', async () => {
    render(<DiagramPanel />, {
      wrapper: getWrapper(
        '/processes?process=eventBasedGatewayProcess&version=all'
      ),
    });

    expect(
      await screen.findByText(
        'There is more than one Version selected for Process "eventBasedGatewayProcess"'
      )
    ).toBeInTheDocument();
  });

  it('should show an error message', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res) =>
        res.networkError('A network error')
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      )
    );

    render(<DiagramPanel />, {
      wrapper: getWrapper(),
    });

    processDiagramStore.fetchProcessDiagram('1');

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/There is no Process selected/)
    ).not.toBeInTheDocument();

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      )
    );

    processDiagramStore.fetchProcessDiagram('2');

    await waitForElementToBeRemoved(screen.getByTestId('diagram-spinner'));

    expect(
      screen.queryByText('Diagram could not be fetched')
    ).not.toBeInTheDocument();

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''), ctx.status(500))
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      )
    );

    processDiagramStore.fetchProcessDiagram('3');

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/There is no Process selected/)
    ).not.toBeInTheDocument();
  });
});
