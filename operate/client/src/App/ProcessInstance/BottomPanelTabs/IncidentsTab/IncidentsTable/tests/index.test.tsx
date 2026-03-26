/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentsTable} from '..';
import {formatDate} from 'modules/utils/date';
import {render, screen, within} from 'modules/testing-library';
import {
  Wrapper,
  incidentsMock,
  firstIncident,
  secondIncident,
} from './mocks';
import {createEnhancedIncident} from 'modules/testUtils';
import {mockFetchProcessInstance as mockFetchProcessInstanceV2} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {createProcessInstance, createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';
import {getIncidentErrorName} from 'modules/utils/incidents';

const firstIncidentErrorName = getIncidentErrorName(firstIncident.errorType);
const secondIncidentErrorName = getIncidentErrorName(secondIncident.errorType);

describe('IncidentsTable', () => {
  beforeEach(() => {
    mockFetchProcessInstanceV2().withSuccess(
      createProcessInstance({
        hasIncident: true,
        parentProcessInstanceKey: null,
        parentElementInstanceKey: null,
        rootProcessInstanceKey: null,
        tags: [],
      }),
    );
  });

  it('should render incident details in row', async () => {
    render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={incidentsMock}
      />,
      {wrapper: Wrapper},
    );
    let withinRow = within(
      screen.getByRole('row', {name: new RegExp(firstIncidentErrorName)}),
    );

    expect(withinRow.getByText(firstIncidentErrorName)).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.elementName)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(firstIncident.creationTime) || '--'),
    ).toBeInTheDocument();
    expect(
      withinRow.getByRole('button', {name: 'Retry Incident'}),
    ).toBeInTheDocument();

    withinRow = within(
      screen.getByRole('row', {name: new RegExp(secondIncidentErrorName)}),
    );
    expect(withinRow.getByText(secondIncidentErrorName)).toBeInTheDocument();
    expect(withinRow.getByText(secondIncident.elementName)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(secondIncident.creationTime) || '--'),
    ).toBeInTheDocument();
    expect(
      withinRow.getByRole('button', {name: 'Retry Incident'}),
    ).toBeInTheDocument();
  });

  it('should show Job ID and error message in expanded row', async () => {
    const {user} = render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={[firstIncident]}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {
        name: /expand current row/i,
      }),
    );

    expect(screen.getByText('Job ID')).toBeInTheDocument();
    expect(screen.getByText(firstIncident.jobKey!)).toBeInTheDocument();
    expect(screen.getByText('Error message')).toBeInTheDocument();
    expect(screen.getByText(firstIncident.errorMessage)).toBeInTheDocument();
  });

  it('should show dash for empty jobKey in expanded row', async () => {
    const incidentMock = {...firstIncident, jobKey: ''};
    const incidents = [incidentMock];

    const {user} = render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={incidents}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {
        name: /expand current row/i,
      }),
    );

    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('should render the right column headers', async () => {
    render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={incidentsMock}
      />,
      {wrapper: Wrapper},
    );

    expect(screen.getByText('Type')).toBeInTheDocument();
    expect(screen.getByText('Failing Element')).toBeInTheDocument();
    expect(screen.getByText('Created')).toBeInTheDocument();
    expect(screen.getByText('Operations')).toBeInTheDocument();
  });

  it('should render the right column headers for restricted user', async () => {
    mockMe().withSuccess(createUser());

    render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={incidentsMock}
      />,
      {wrapper: Wrapper},
    );

    expect(screen.getByText('Type')).toBeInTheDocument();
    expect(screen.getByText('Failing Element')).toBeInTheDocument();
    expect(screen.getByText('Created')).toBeInTheDocument();
    expect(screen.getByText('Operations')).toBeInTheDocument();
  });

  it('should provide a link for incidents in child process instances', () => {
    render(
      <IncidentsTable
        state="content"
        processInstanceKey="7"
        incidents={incidentsMock}
      />,
      {wrapper: Wrapper},
    );
    let withinRow = within(
      screen.getByRole('row', {name: new RegExp(firstIncidentErrorName)}),
    );

    expect(
      withinRow.getByRole('link', {
        name: `${firstIncident.elementId} - ${firstIncident.processDefinitionName} - ${firstIncident.processInstanceKey}`,
        description: `View root cause instance ${firstIncident.processDefinitionName} - ${firstIncident.processInstanceKey}`,
      }),
    ).toBeInTheDocument();
  });

  it('should open a modal when clicking on the more button in expanded row', async () => {
    const {user} = render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={incidentsMock}
      />,
      {wrapper: Wrapper},
    );

    const expandButtons = screen.getAllByRole('button', {
      name: /expand current row/i,
    });

    await user.click(expandButtons[1]!);

    expect(screen.getByText('More')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await user.click(screen.getByText('More'));

    const modal = screen.getByRole('dialog');

    expect(
      await within(modal).findByTestId('monaco-editor'),
    ).toBeInTheDocument();
    expect(
      within(modal).getByText(`Element "${secondIncident.elementName}" Error`),
    ).toBeInTheDocument();
  });

  it('should provide a retry operation for incidents in the open process instance', async () => {
    render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={[
          {...firstIncident, processInstanceKey: '1'},
          {...secondIncident, processInstanceKey: '2'},
        ]}
      />,
      {wrapper: Wrapper},
    );
    let firstIncidentRow = within(
      screen.getByRole('row', {name: new RegExp(firstIncidentErrorName)}),
    );
    let secondIncidentRow = within(
      screen.getByRole('row', {name: new RegExp(secondIncidentErrorName)}),
    );

    expect(
      await firstIncidentRow.findByRole('button', {name: 'Retry Incident'}),
    ).toBeInTheDocument();
    expect(
      secondIncidentRow.queryByRole('button', {name: 'Retry Incident'}),
    ).not.toBeInTheDocument();
  });

  it('should provide a link to root cause decision instance for DECISION_EVALUATION_ERROR', () => {
    const decisionIncident = createEnhancedIncident({
      errorType: 'DECISION_EVALUATION_ERROR',
      processInstanceKey: '1',
      errorMessage: 'Decision failed',
      elementId: 'businessRuleTask_1',
      elementInstanceKey: 'elementKey_123',
    });

    render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        decisionInstancesByElementKey={{
          elementKey_123: {
            decisionInstanceKey: 'decisionKey_456',
            decisionDefinitionName: 'MyDecision',
          },
        }}
        incidents={[decisionIncident]}
      />,
      {wrapper: Wrapper},
    );

    const withinRow = within(
      screen.getByRole('row', {
        name: /Decision evaluation error/i,
      }),
    );

    expect(
      withinRow.getByRole('link', {
        name: 'MyDecision - decisionKey_456',
      }),
    ).toBeInTheDocument();
  });

  it('should provide a link to root cause decision instance for CALLED_DECISION_ERROR', () => {
    const decisionIncident = createEnhancedIncident({
      errorType: 'CALLED_DECISION_ERROR',
      processInstanceKey: '1',
      errorMessage: 'Decision not found',
      elementId: 'businessRuleTask_2',
      elementInstanceKey: 'elementKey_789',
    });

    render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        decisionInstancesByElementKey={{
          elementKey_789: {
            decisionInstanceKey: 'decisionKey_101',
            decisionDefinitionName: 'AnotherDecision',
          },
        }}
        incidents={[decisionIncident]}
      />,
      {wrapper: Wrapper},
    );

    const withinRow = within(
      screen.getByRole('row', {
        name: /Called decision error/i,
      }),
    );

    expect(
      withinRow.getByRole('link', {
        name: 'AnotherDecision - decisionKey_101',
      }),
    ).toBeInTheDocument();
  });

  it('should show plain element name for decision incidents without matching decision instance', () => {
    render(
      <IncidentsTable
        state="content"
        processInstanceKey="1"
        incidents={[secondIncident]}
      />,
      {wrapper: Wrapper},
    );

    const withinRow = within(
      screen.getByRole('row', {name: new RegExp(secondIncidentErrorName)}),
    );

    expect(withinRow.getByText(secondIncident.elementName)).toBeInTheDocument();
    expect(withinRow.queryByRole('link')).not.toBeInTheDocument();
  });
});
