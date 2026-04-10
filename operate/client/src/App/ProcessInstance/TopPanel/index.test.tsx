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
} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {TopPanel} from './index';
import {modificationsStore} from 'modules/stores/modifications';
import {searchResult} from 'modules/testUtils';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {open} from 'modules/mocks/diagrams';
import {Paths} from 'modules/Routes';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessSequenceFlows} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/sequenceFlows';
import type {
  ElementInstance,
  ProcessInstance,
  SequenceFlow,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {mockSearchMessageSubscriptions} from 'modules/mocks/api/v2/messageSubscriptions/searchMessageSubscriptions';
import {
  SearchParamsUpdater,
  updateSearchParams,
} from 'modules/testUtils/SearchParamsUpdater';

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
  endDate: null,
  processDefinitionKey: '2',
  processDefinitionVersion: 1,
  processDefinitionVersionTag: null,
  processDefinitionId: 'someKey',
  tenantId: '<default>',
  processDefinitionName: 'someProcessName',
  hasIncident: true,
  parentProcessInstanceKey: null,
  parentElementInstanceKey: null,
  rootProcessInstanceKey: null,
  tags: [],
};

const mockElementInstance: ElementInstance = {
  elementInstanceKey: '2251799813699889',
  elementId: 'service-task-1',
  elementName: 'Service Task',
  type: 'SERVICE_TASK',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  endDate: null,
  processDefinitionId: 'process-def-1',
  processInstanceKey: 'instance_id',
  processDefinitionKey: '2',
  rootProcessInstanceKey: null,
  hasIncident: false,
  incidentKey: null,
  tenantId: '<default>',
};

const getWrapper = (searchParams?: Record<string, string>) => {
  let initialPath = Paths.processInstance('1');
  if (searchParams) {
    const search = new URLSearchParams(searchParams).toString();
    initialPath += `?${search}`;
  }

  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={[initialPath]}>
            <Routes>
              <Route
                path={Paths.processInstance()}
                element={
                  <>
                    <SearchParamsUpdater />
                    {children}
                  </>
                }
              />
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

    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessSequenceFlows().withSuccess({items: mockSequenceFlowsV2});
    mockFetchElementInstancesStatistics().withSuccess({
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

    mockSearchJobs().withSuccess(searchResult([]));
    mockSearchDecisionInstances().withSuccess(searchResult([]));
    mockSearchProcessInstances().withSuccess(searchResult([]));

    mockSearchMessageSubscriptions().withSuccess(searchResult([]));

    mockSearchElementInstances().withSuccess(searchResult([]));
  });

  afterEach(() => {
    modificationsStore.reset();
  });

  it('should render spinner while loading', async () => {
    mockFetchProcessInstance().withSuccess({
      ...mockProcessInstance,
      hasIncident: false,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
    });

    render(<TopPanel />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.queryByTestId('diagram-spinner'));
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

  it('should render modification dropdown in modification mode', async () => {
    mockFetchElementInstance('2251799813699889').withSuccess(
      mockElementInstance,
    );
    mockSearchElementInstances().withSuccess(
      searchResult([mockElementInstance]),
    );

    modificationsStore.enableModificationMode();

    render(<TopPanel />, {
      wrapper: getWrapper({
        elementId: 'service-task-1',
        elementInstanceKey: '2251799813699889',
      }),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('diagram-spinner'),
    );

    expect(screen.queryByText(/Start Date/)).not.toBeInTheDocument();
    expect(screen.queryByText(/End Date/)).not.toBeInTheDocument();

    updateSearchParams({elementId: 'service-task-1'});

    expect(
      await screen.findByText(/Element Modifications/),
    ).toBeInTheDocument();
    expect(
      await screen.findByTitle(/Add single element instance/),
    ).toBeInTheDocument();
    expect(
      screen.getByTitle(/Cancel selected instance in this element/),
    ).toBeInTheDocument();

    expect(
      screen.getByTitle(
        /Move selected instance in this element to another target/,
      ),
    ).toBeInTheDocument();
  });

  it('should display move token banner in moving mode', async () => {
    mockSearchElementInstances().withSuccess(
      searchResult([mockElementInstance]),
    );

    modificationsStore.enableModificationMode();

    const {user} = render(<TopPanel />, {
      wrapper: getWrapper({elementId: 'service-task-1'}),
    });

    expect(
      await screen.findByText(/Element Modifications/),
    ).toBeInTheDocument();

    expect(
      screen.queryByText(/select the target element in the diagram/i),
    ).not.toBeInTheDocument();

    await user.click(await screen.findByRole('button', {name: /move/i}));

    expect(
      await screen.findByText(/select the target element in the diagram/i),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Discard'}));

    expect(
      screen.queryByText(/select the target element in the diagram/i),
    ).not.toBeInTheDocument();
  });

  it('should pass the ancestor type if move modification requires an ancestor', async () => {
    mockFetchProcessDefinitionXml().withSuccess(
      open('subprocessInsideMultiInstance.bpmn'),
    );

    const parentElement = {
      elementId: 'sub-2',
    };
    const element: ElementInstance = {
      ...mockElementInstance,
      elementInstanceKey: '2251799813699889',
      elementId: 'task-1',
      elementName: 'Task 1',
    };

    mockFetchElementInstance('2251799813699889').withSuccess(element);
    mockSearchElementInstances().withSuccess(searchResult([element]));

    mockFetchElementInstancesStatistics().withSuccess({
      items: [
        {
          elementId: element.elementId,
          active: 2,
          completed: 0,
          canceled: 0,
          incidents: 0,
        },
        {
          elementId: parentElement.elementId,
          active: 2,
          completed: 0,
          canceled: 0,
          incidents: 0,
        },
      ],
    });

    modificationsStore.enableModificationMode();

    const {user} = render(<TopPanel />, {
      wrapper: getWrapper({
        elementId: element.elementId,
        elementInstanceKey: element.elementInstanceKey,
      }),
    });

    expect(
      screen.queryByText(/select the target element in the diagram/i),
    ).not.toBeInTheDocument();

    await user.click(await screen.findByRole('button', {name: /move/i}));

    expect(
      await screen.findByText(/select the target element in the diagram/i),
    ).toBeInTheDocument();

    await user.click(await screen.findByTestId('task-2'));

    const modifications = modificationsStore.state.modifications;
    expect(modifications).toHaveLength(1);
    expect(modifications[0].payload).toMatchObject({
      operation: 'MOVE_TOKEN',
      ancestorScopeType: 'sourceParent',
    });
  });
});
