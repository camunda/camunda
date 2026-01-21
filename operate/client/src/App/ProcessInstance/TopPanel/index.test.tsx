/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  waitForElementToBeRemoved,
  screen,
  waitFor,
} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {TopPanel} from './index';
import {modificationsStore} from 'modules/stores/modifications';
import {calledInstanceMetadata} from 'modules/mocks/metadata';
import {createInstance, createIncident} from 'modules/testUtils';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {open} from 'modules/mocks/diagrams';
import {Paths} from 'modules/Routes';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessSequenceFlows} from 'modules/mocks/api/v2/flownodeInstances/sequenceFlows';
import type {
  ProcessInstance,
  SequenceFlow,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {http, HttpResponse} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {mockSearchIncidentsByProcessInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByProcessInstance';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {mockSearchMessageSubscriptions} from 'modules/mocks/api/v2/messageSubscriptions/searchMessageSubscriptions';

const mockIncidents = {
  page: {totalItems: 1},
  items: [
    createIncident({errorType: 'CONDITION_ERROR', elementId: 'Service5678'}),
  ],
};

const mockSequenceFlowsV2: SequenceFlow[] = [
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowId: 'SequenceFlow_0drux68',
    processDefinitionId: '123',
    processDefinitionKey: '123',
    tenantId: '',
    elementId: '',
  },
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowId: 'SequenceFlow_0j6tsnn',
    processDefinitionId: '123',
    processDefinitionKey: '123',
    tenantId: '',
    elementId: '',
  },
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowId: 'SequenceFlow_1dwqvrt',
    processDefinitionId: '123',
    processDefinitionKey: '123',
    tenantId: '',
    elementId: '',
  },
  {
    processInstanceKey: '2251799813693731',
    sequenceFlowId: 'SequenceFlow_1fgekwd',
    processDefinitionId: '123',
    processDefinitionKey: '123',
    tenantId: '',
    elementId: '',
  },
];

vi.mock('react-transition-group', () => {
  const FakeTransition = vi.fn(({children}) => children);
  const FakeCSSTransition = vi.fn((props) =>
    props.in ? <FakeTransition>{props.children}</FakeTransition> : null,
  );

  return {
    CSSTransition: FakeCSSTransition,
    Transition: FakeTransition,
    TransitionGroup: vi.fn(({children}) => {
      return children.map((transition: {props: object}) => {
        const completedTransition = {...transition};
        completedTransition.props = {...transition.props, in: true};
        return completedTransition;
      });
    }),
  };
});

const mockProcessInstance: ProcessInstance = {
  processInstanceKey: 'instance_id',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionKey: '2',
  processDefinitionVersion: 1,
  processDefinitionId: 'someKey',
  tenantId: '<default>',
  processDefinitionName: 'someProcessName',
  hasIncident: true,
};

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('1')],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };
  return Wrapper;
};

describe('TopPanel', () => {
  beforeAll(() => {
    //@ts-expect-error - Use to mute act warnings
    // eslint-disable-next-line no-undef
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-expect-error - Use to mute act warnings
    // eslint-disable-next-line no-undef
    IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess(
      open('diagramForModifications.bpmn'),
    );

    mockFetchProcessInstanceDeprecated().withSuccess(
      createInstance({id: 'instance_id', state: 'INCIDENT'}),
    );
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockSearchIncidentsByProcessInstance(':instance_id').withSuccess(
      mockIncidents,
    );
    mockSearchIncidentsByProcessInstance(':instance_id').withSuccess(
      mockIncidents,
    );
    mockSearchIncidentsByProcessInstance(':instance_id').withSuccess(
      mockIncidents,
    );
    mockFetchProcessSequenceFlows().withSuccess({items: mockSequenceFlowsV2});
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'service-task-1',
          active: 0,
          incidents: 1,
          completed: 0,
          canceled: 0,
        },
        {
          elementId: 'service-task-7',
          active: 5,
          incidents: 1,
          completed: 0,
          canceled: 0,
        },
      ],
    });

    mockSearchJobs().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchDecisionInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockSearchMessageSubscriptions().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    mockServer.use(
      http.post('/api/process-instances/:instanceId/flow-node-metadata', () => {
        return HttpResponse.json(calledInstanceMetadata);
      }),
    );
  });

  afterEach(() => {
    modificationsStore.reset();
  });

  it('should render spinner while loading', async () => {
    mockFetchProcessInstance().withSuccess({
      ...mockProcessInstance,
      hasIncident: false,
    });

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.queryByTestId('diagram-spinner'));
  });

  it('should render incident bar', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('1 Incident occurred')).toBeInTheDocument();
  });

  it('should show an error when a server error occurs', async () => {
    mockFetchProcessDefinitionXml().withServerError();
    mockFetchProcessDefinitionXml().withServerError();

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should show an error when a network error occurs', async () => {
    const consoleErrorMock = vi
      .spyOn(global.console, 'error')
      .mockImplementation(() => {});

    mockFetchProcessDefinitionXml().withNetworkError();

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });

  it('should show permissions error when access to the process definition is forbidden', async () => {
    mockFetchProcessDefinitionXml().withServerError(403);
    mockFetchProcessDefinitionXml().withServerError(403);

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByText('Missing permissions to view the Definition'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Please contact your organization owner or admin to give you the necessary permissions to read this definition',
      ),
    ).toBeInTheDocument();
  });

  it('should toggle incident bar', async () => {
    mockFetchProcessInstanceDeprecated().withSuccess(
      createInstance({id: 'instance_id', state: 'INCIDENT'}),
    );

    const {user} = render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(
        screen.queryByText('Incidents - 1 result'),
      ).not.toBeInTheDocument(),
    );

    await user.click(await screen.findByTitle('View 1 Incident in Instance 1'));

    expect(await screen.findByText('Incidents - 1 result')).toBeInTheDocument();

    await user.click(await screen.findByTitle('View 1 Incident in Instance 1'));

    expect(screen.queryByText('Incidents - 1 result')).not.toBeInTheDocument();
  });
});
