/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  render,
  waitForElementToBeRemoved,
  screen,
} from '@testing-library/react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {mockSequenceFlows, mockIncidents} from './index.setup';
import SplitPane from 'modules/components/SplitPane';
import {TopPanel} from './index';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import userEvent from '@testing-library/user-event';

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
jest.mock('./InstanceHeader', () => {
  return {
    InstanceHeader: () => {
      return <div />;
    },
  };
});

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/instances/1']}>
        <Routes>
          <Route
            path="/instances/:processInstanceId"
            element={
              <SplitPane>
                {children}
                <SplitPane.Pane />
              </SplitPane>
            }
          />
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
    singleInstanceDiagramStore.reset();
    currentInstanceStore.reset();
  });

  it('should render spinner while loading', async () => {
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    currentInstanceStore.init({id: 'active_instance'});

    singleInstanceDiagramStore.fetchProcessXml('1');
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('diagram-spinner'));
  });

  it('should render incident bar', async () => {
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    currentInstanceStore.init({id: 'instance_with_incident'});
    await singleInstanceDiagramStore.fetchProcessXml('1');
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

    currentInstanceStore.init({id: 'instance_with_incident'});
    singleInstanceDiagramStore.fetchProcessXml('1');

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

    currentInstanceStore.init({id: 'instance_with_incident'});
    singleInstanceDiagramStore.fetchProcessXml('1');

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();
  });

  it('should toggle incident bar', async () => {
    render(<TopPanel />, {
      wrapper: Wrapper,
    });

    currentInstanceStore.init({id: 'instance_with_incident'});
    await singleInstanceDiagramStore.fetchProcessXml('1');

    expect(screen.queryByText('Incident Type:')).not.toBeInTheDocument();
    expect(screen.queryByText('Flow Node:')).not.toBeInTheDocument();

    userEvent.click(await screen.findByTitle('View 1 Incident in Instance 1'));

    expect(screen.getByText('Incident type:')).toBeInTheDocument();
    expect(screen.getByText('Flow Node:')).toBeInTheDocument();

    userEvent.click(await screen.findByTitle('View 1 Incident in Instance 1'));

    expect(screen.queryByText('Incident type:')).not.toBeInTheDocument();
    expect(screen.queryByText('Flow Node:')).not.toBeInTheDocument();
  });
});
