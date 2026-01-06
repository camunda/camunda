/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {Incidents} from './index';
import {mockSearchIncidentsByElementInstance} from 'modules/mocks/api/v2/incidents/searchIncidentsByElementInstance';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import type {
  DecisionInstance,
  Incident,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {incidentsStore} from 'modules/stores/incidents';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {Paths} from 'modules/Routes';

const ELEMENT_INSTANCE_KEY = '44444444444444444';
const ELEMENT_ID = 'ServiceTask_1';
const ELEMENT_NAME = 'Service Task 1';
const PROCESS_INSTANCE_KEY = '1111111111111111';

const mockSingleIncident: Incident = {
  incidentKey: '1',
  processInstanceKey: PROCESS_INSTANCE_KEY,
  processDefinitionKey: '2222222222222222',
  processDefinitionId: 'testProcess',
  errorType: 'JOB_NO_RETRIES',
  errorMessage: 'Failed to execute job',
  elementId: ELEMENT_ID,
  elementInstanceKey: ELEMENT_INSTANCE_KEY,
  jobKey: '33333333333333333',
  creationTime: '2024-10-28T10:00:00.000Z',
  state: 'ACTIVE',
  tenantId: '<default>',
};

const mockSingleIncidentCalledProcessInstance: Incident = {
  ...mockSingleIncident,
  processInstanceKey: '55555555555555555',
  processDefinitionId: 'calledProcess',
};

const mockSingleIncidentCalledDecisionInstance: Incident = {
  ...mockSingleIncident,
  errorType: 'DECISION_EVALUATION_ERROR',
};

const mockMultipleIncidents: Incident[] = [
  {
    ...mockSingleIncident,
    incidentKey: '1',
    errorType: 'JOB_NO_RETRIES',
    errorMessage: 'First incident',
  },
  {
    ...mockSingleIncident,
    incidentKey: '2',
    errorType: 'CONDITION_ERROR',
    errorMessage: 'Second incident',
  },
  {
    ...mockSingleIncident,
    incidentKey: '3',
    errorType: 'EXTRACT_VALUE_ERROR',
    errorMessage: 'Third incident',
  },
];

const mockDecisionInstance: DecisionInstance = {
  decisionEvaluationInstanceKey: '88888888888888888',
  decisionEvaluationKey: '99999999999999999',
  state: 'FAILED',
  evaluationDate: '2024-10-28T10:30:00.000Z',
  evaluationFailure:
    'Failed to evaluate decision: Expression evaluation failed',
  decisionDefinitionId: 'testDecision',
  decisionDefinitionKey: '77777777777777777',
  decisionDefinitionName: 'Test Decision',
  decisionDefinitionVersion: 1,
  decisionDefinitionType: 'DECISION_TABLE',
  result: '',
  tenantId: '<default>',
  processDefinitionKey: '2222222222222222',
  processInstanceKey: PROCESS_INSTANCE_KEY,
  elementInstanceKey: ELEMENT_INSTANCE_KEY,
  rootDecisionDefinitionKey: '77777777777777777',
};

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter
        initialEntries={[Paths.processInstance(PROCESS_INSTANCE_KEY)]}
      >
        <Routes>
          <Route path={Paths.processInstance()} element={<>{children}</>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('<Incidents />', () => {
  beforeEach(() => {
    incidentsStore.reset();
    mockFetchProcessInstance().withSuccess({
      processInstanceKey: PROCESS_INSTANCE_KEY,
      state: 'ACTIVE',
      startDate: '2024-10-28T10:00:00.000Z',
      processDefinitionKey: '200',
      processDefinitionVersion: 1,
      processDefinitionId: 'testProcess',
      tenantId: '<default>',
      processDefinitionName: 'Test Process',
      hasIncident: true,
    });
  });

  it('should render loading state', () => {
    mockSearchIncidentsByElementInstance(ELEMENT_INSTANCE_KEY).withDelay({
      items: [],
      page: {totalItems: 0},
    });

    render(
      <Incidents
        elementInstanceKey={ELEMENT_INSTANCE_KEY}
        elementName={ELEMENT_NAME}
        elementId={ELEMENT_ID}
      />,
      {wrapper: Wrapper},
    );

    expect(screen.getByTestId('incidents-loading')).toBeInTheDocument();
  });

  it('should render single incident - current instance', async () => {
    mockSearchIncidentsByElementInstance(ELEMENT_INSTANCE_KEY).withSuccess({
      items: [mockSingleIncident],
      page: {totalItems: 1},
    });

    render(
      <Incidents
        elementInstanceKey={ELEMENT_INSTANCE_KEY}
        elementName={ELEMENT_NAME}
        elementId={ELEMENT_ID}
      />,
      {wrapper: Wrapper},
    );

    expect(
      await screen.findByRole('heading', {name: /^incident$/i}),
    ).toBeInTheDocument();

    expect(screen.getByText(/No more retries left./i)).toBeInTheDocument();
    expect(screen.getByText(/Failed to execute job/i)).toBeInTheDocument();
    expect(screen.getByText(/Error Message/i)).toBeInTheDocument();
    expect(
      screen.getByText(/Root Cause Process Instance/i),
    ).toBeInTheDocument();
    expect(await screen.findByText(/Current instance/i)).toBeInTheDocument();
  });

  it('should render single incident - called process instance', async () => {
    mockSearchIncidentsByElementInstance(ELEMENT_INSTANCE_KEY).withSuccess({
      items: [mockSingleIncidentCalledProcessInstance],
      page: {totalItems: 1},
    });

    render(
      <Incidents
        elementInstanceKey={ELEMENT_INSTANCE_KEY}
        elementName={ELEMENT_NAME}
        elementId={ELEMENT_ID}
      />,
      {wrapper: Wrapper},
    );

    expect(
      await screen.findByRole('heading', {name: /^incident$/i}),
    ).toBeInTheDocument();

    expect(screen.getByText(/No more retries left./i)).toBeInTheDocument();
    expect(screen.getByText(/Failed to execute job/i)).toBeInTheDocument();
    expect(screen.getByText(/Error Message/i)).toBeInTheDocument();
    expect(
      screen.getByText(/Root Cause Process Instance/i),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('link', {
        name: /calledProcess - 55555555555555555/i,
      }),
    ).toBeInTheDocument();
  });

  it('should render single incident - called decision instance', async () => {
    mockSearchIncidentsByElementInstance(ELEMENT_INSTANCE_KEY).withSuccess({
      items: [mockSingleIncidentCalledDecisionInstance],
      page: {totalItems: 1},
    });

    mockSearchDecisionInstances().withSuccess({
      items: [mockDecisionInstance],
      page: {totalItems: 1},
    });

    render(
      <Incidents
        elementInstanceKey={ELEMENT_INSTANCE_KEY}
        elementName={ELEMENT_NAME}
        elementId={ELEMENT_ID}
      />,
      {wrapper: Wrapper},
    );

    expect(
      await screen.findByRole('heading', {name: /^incident$/i}),
    ).toBeInTheDocument();

    expect(
      await screen.findByText(/Root Cause Decision Instance/i),
    ).toBeInTheDocument();
  });

  it('should render multi incidents', async () => {
    mockSearchIncidentsByElementInstance(ELEMENT_INSTANCE_KEY).withSuccess({
      items: mockMultipleIncidents,
      page: {totalItems: 3},
    });

    render(
      <Incidents
        elementInstanceKey={ELEMENT_INSTANCE_KEY}
        elementName={ELEMENT_NAME}
        elementId={ELEMENT_ID}
      />,
      {wrapper: Wrapper},
    );

    expect(
      await screen.findByRole('heading', {name: /^incidents$/i}),
    ).toBeInTheDocument();

    expect(screen.getByText('3 incidents occurred')).toBeInTheDocument();
  });

  it('should render no incident section when no incidents', async () => {
    mockSearchIncidentsByElementInstance(ELEMENT_INSTANCE_KEY).withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    render(
      <Incidents
        elementInstanceKey={ELEMENT_INSTANCE_KEY}
        elementName={ELEMENT_NAME}
        elementId={ELEMENT_ID}
      />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('incidents-loading'),
    );

    expect(
      screen.queryByRole('heading', {name: /^incidents$/i}),
    ).not.toBeInTheDocument();
  });
});
