/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  waitForElementToBeRemoved,
  screen,
} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {mockSequenceFlows, mockIncidents} from './index.setup';
import {TopPanel} from './index';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';

jest.mock('react-transition-group', () => {
  const FakeTransition = jest.fn(({children}) => children);
  const FakeCSSTransition = jest.fn((props) =>
    props.in ? <FakeTransition>{props.children}</FakeTransition> : null
  );

  return {
    CSSTransition: FakeCSSTransition,
    Transition: FakeTransition,
    TransitionGroup: jest.fn(({children}) => {
      return children.map((transition: any) => {
        const completedTransition = {...transition};
        completedTransition.props = {...transition.props, in: true};
        return completedTransition;
      });
    }),
  };
});
jest.mock('modules/utils/bpmn');

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>
        <Routes>
          <Route path="/processes/:processInstanceId" element={children} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('TopPanel', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/process-instances/active_instance', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'instance_id',
            state: 'ACTIVE',
          })
        )
      ),
      rest.get('/api/process-instances/instance_with_incident', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'instance_id',
            state: 'INCIDENT',
          })
        )
      ),
      rest.get('/api/process-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(ctx.json(mockIncidents))
      ),
      rest.get(
        '/api/process-instances/:instanceId/sequence-flows',
        (_, res, ctx) => res.once(ctx.json(mockSequenceFlows))
      ),
      rest.get(
        '/api/process-instances/:instanceId/flow-node-states',
        (_, res, ctx) => res.once(ctx.json({}))
      )
    );
  });

  afterEach(() => {
    processInstanceDetailsDiagramStore.reset();
    processInstanceDetailsStore.reset();
  });

  it('should render spinner while loading', async () => {
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({id: 'active_instance'});

    processInstanceDetailsDiagramStore.fetchProcessXml('1');
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('diagram-spinner'));
  });

  it('should render incident bar', async () => {
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');
    expect(await screen.findByText('1 Incident occured')).toBeInTheDocument();
  });

  it('should show an error when a server error occurs', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''), ctx.status(500))
      )
    );
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});
    processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();
  });

  it('should show an error when a network error occurs', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.networkError('A network error')
      )
    );
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});
    processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();
  });

  it('should toggle incident bar', async () => {
    const {user} = render(<TopPanel />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({id: 'instance_with_incident'});
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(screen.queryByText('Incident Type:')).not.toBeInTheDocument();
    expect(screen.queryByText('Flow Node:')).not.toBeInTheDocument();

    await user.click(await screen.findByTitle('View 1 Incident in Instance 1'));

    expect(screen.getByText('Incident type:')).toBeInTheDocument();
    expect(screen.getByText('Flow Node:')).toBeInTheDocument();

    await user.click(await screen.findByTitle('View 1 Incident in Instance 1'));

    expect(screen.queryByText('Incident type:')).not.toBeInTheDocument();
    expect(screen.queryByText('Flow Node:')).not.toBeInTheDocument();
  });
});
