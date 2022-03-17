/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {IncidentsTable} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {createIncident, mockCallActivityProcessXML} from 'modules/testUtils';
import {formatDate} from 'modules/utils/date';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {authenticationStore} from 'modules/stores/authentication';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {mockServer} from 'modules/mock-server/node';
import {rest} from 'msw';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';

const id = 'flowNodeInstanceIdB';
const shortError = 'No data found for query $.orderId.';
const longError =
  'Cannot compare values of different types: INTEGER and BOOLEAN';
const incidentsMock = [
  createIncident({
    errorType: {name: 'Error A', id: 'ERROR_A'},
    errorMessage: shortError,
    flowNodeName: 'StartEvent_1',
    flowNodeId: 'StartEvent_1',
    flowNodeInstanceId: '18239123812938',
    rootCauseInstance: {
      instanceId: '111111111111111111',
      processDefinitionId: 'calledInstance',
      processDefinitionName: 'Called Instance',
    },
  }),
  createIncident({
    errorType: {name: 'Error B', id: 'ERROR_A'},
    errorMessage: longError,
    flowNodeId: 'Event_1db567d',
    flowNodeName: 'Event_1db567d',
    flowNodeInstanceId: id,
  }),
] as const;

const Wrapper: React.FC = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/instances/1']}>
        <Routes>
          <Route path="/instances/:processInstanceId" element={children} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('IncidentsTable', () => {
  afterEach(() => {
    incidentsStore.reset();
    authenticationStore.reset();
    flowNodeSelectionStore.reset();
  });

  it('should render the right column headers', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockCallActivityProcessXML))
      )
    );
    incidentsStore.setIncidents({incidents: incidentsMock, count: 2});

    await singleInstanceDiagramStore.fetchProcessXml('1');

    render(<IncidentsTable />, {wrapper: Wrapper});

    expect(screen.getByText('Incident Type')).toBeInTheDocument();
    expect(screen.getByText('Flow Node')).toBeInTheDocument();
    expect(screen.getByText('Job Id')).toBeInTheDocument();
    expect(screen.getByText('Creation Time')).toBeInTheDocument();
    expect(screen.getByText('Error Message')).toBeInTheDocument();
    expect(screen.getByText('Operations')).toBeInTheDocument();
    expect(screen.getByText('Root Cause Instance')).toBeInTheDocument();
  });

  it('should render the right column headers for restricted user', () => {
    incidentsStore.setIncidents({incidents: incidentsMock, count: 2});
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
    });

    render(<IncidentsTable />, {wrapper: Wrapper});

    expect(screen.getByText('Incident Type')).toBeInTheDocument();
    expect(screen.getByText('Flow Node')).toBeInTheDocument();
    expect(screen.getByText('Job Id')).toBeInTheDocument();
    expect(screen.getByText('Creation Time')).toBeInTheDocument();
    expect(screen.getByText('Error Message')).toBeInTheDocument();
    expect(screen.queryByText('Operations')).not.toBeInTheDocument();
    expect(screen.getByText('Root Cause Instance')).toBeInTheDocument();
  });

  it('should render incident details', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockCallActivityProcessXML))
      )
    );
    incidentsStore.setIncidents({incidents: incidentsMock, count: 2});
    await singleInstanceDiagramStore.fetchProcessXml('1');

    render(<IncidentsTable />, {wrapper: Wrapper});
    let withinRow = within(
      screen.getByTestId(`tr-incident-${incidentsMock[0].id}`)
    );

    expect(
      withinRow.getByText(incidentsMock[0].errorType.name)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(incidentsMock[0].flowNodeName)
    ).toBeInTheDocument();
    expect(withinRow.getByText(incidentsMock[0].jobId)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(incidentsMock[0].creationTime) || '--')
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(incidentsMock[0].errorMessage)
    ).toBeInTheDocument();
    expect(
      withinRow.getByRole('link', {
        name: /view root cause instance/i,
      })
    ).toBeInTheDocument();
    expect(
      withinRow.queryByRole('button', {name: 'Retry Incident'})
    ).not.toBeInTheDocument();
    withinRow = within(
      screen.getByTestId(`tr-incident-${incidentsMock[1].id}`)
    );
    expect(
      withinRow.getByText(incidentsMock[1].errorType.name)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(incidentsMock[1].flowNodeName)
    ).toBeInTheDocument();
    expect(withinRow.getByText(incidentsMock[1].jobId)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(incidentsMock[1].creationTime) || '--')
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(incidentsMock[1].errorMessage)
    ).toBeInTheDocument();
    expect(
      withinRow.getByRole('button', {name: 'Retry Incident'})
    ).toBeInTheDocument();
  });

  it('should render incident details for restricted user', () => {
    incidentsStore.setIncidents({incidents: incidentsMock, count: 2});
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
    });

    render(<IncidentsTable />, {wrapper: Wrapper});
    let withinRow = within(
      screen.getByTestId(`tr-incident-${incidentsMock[0].id}`)
    );

    expect(
      withinRow.getByText(incidentsMock[0].errorType.name)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(incidentsMock[0].flowNodeName)
    ).toBeInTheDocument();
    expect(withinRow.getByText(incidentsMock[0].jobId)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(incidentsMock[0].creationTime) || '--')
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(incidentsMock[0].errorMessage)
    ).toBeInTheDocument();
    expect(
      withinRow.getByRole('link', {
        name: /view root cause instance/i,
      })
    ).toBeInTheDocument();
    expect(
      withinRow.queryByRole('button', {name: 'Retry Incident'})
    ).not.toBeInTheDocument();

    withinRow = within(
      screen.getByTestId(`tr-incident-${incidentsMock[1].id}`)
    );
    expect(
      withinRow.getByText(incidentsMock[1].errorType.name)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(incidentsMock[1].flowNodeName)
    ).toBeInTheDocument();
    expect(withinRow.getByText(incidentsMock[1].jobId)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(incidentsMock[1].creationTime) || '--')
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(incidentsMock[1].errorMessage)
    ).toBeInTheDocument();
    expect(
      withinRow.queryByRole('button', {name: 'Retry Incident'})
    ).not.toBeInTheDocument();
    expect(
      withinRow.queryByRole('link', {
        name: /view root cause instance/i,
      })
    ).not.toBeInTheDocument();
  });

  it('should display -- for jobId', () => {
    const incidents = [
      createIncident({
        errorType: 'Error A',
        errorMessage: shortError,
        flowNodeName: 'StartEvent_1',
        flowNodeId: 'StartEvent_1',
        flowNodeInstanceId: '18239123812938',
        rootCauseInstance: null,
        jobId: null,
      }),
    ] as const;

    incidentsStore.setIncidents({incidents, count: 1});

    render(<IncidentsTable />, {wrapper: Wrapper});

    let withinFirstRow = within(
      screen.getByTestId(`tr-incident-${incidents[0].id}`)
    );

    expect(withinFirstRow.getByText('--')).toBeInTheDocument();
  });

  it('should show a more button for long error messages', () => {
    incidentsStore.setIncidents({incidents: incidentsMock, count: 2});
    render(<IncidentsTable />, {wrapper: Wrapper});
    let withinFirstRow = within(
      screen.getByTestId(`tr-incident-${incidentsMock[0].id}`)
    );

    expect(withinFirstRow.queryByText('More...')).not.toBeInTheDocument();

    let withinSecondRow = within(
      screen.getByTestId(`tr-incident-${incidentsMock[1].id}`)
    );

    expect(withinSecondRow.getByText('More...')).toBeInTheDocument();
  });

  it('should open an modal when clicking on the more button', () => {
    incidentsStore.setIncidents({incidents: incidentsMock, count: 2});
    render(<IncidentsTable />, {wrapper: Wrapper});

    let withinSecondRow = within(
      screen.getByTestId(`tr-incident-${incidentsMock[1].id}`)
    );

    expect(withinSecondRow.getByText('More...')).toBeInTheDocument();

    expect(screen.queryByTestId('modal')).not.toBeInTheDocument();

    userEvent.click(withinSecondRow.getByText('More...'));

    const modal = screen.getByTestId('modal');
    expect(
      within(modal).getByText(
        `Flow Node "${incidentsMock[1].flowNodeName}" Error`
      )
    ).toBeInTheDocument();
    expect(
      within(modal).getByText(incidentsMock[1].errorMessage)
    ).toBeInTheDocument();
  });

  describe('Sorting', () => {
    it('should enable sorting for all', () => {
      incidentsStore.setIncidents({incidents: incidentsMock, count: 2});
      render(<IncidentsTable />, {wrapper: Wrapper});

      expect(screen.getByText('Job Id')).toBeEnabled();
      expect(screen.getByText('Incident Type')).toBeEnabled();
      expect(screen.getByText('Flow Node')).toBeEnabled();
      expect(screen.getByText('Job Id')).toBeEnabled();
      expect(screen.getByText('Creation Time')).toBeEnabled();
      expect(screen.getByText('Error Message')).toBeEnabled();
      expect(screen.getByText('Operations')).toBeEnabled();
    });

    it('should disable sorting for jobId', () => {
      const incidents = [
        createIncident({
          errorType: 'Error A',
          errorMessage: shortError,
          flowNodeName: 'Task A',
          flowNodeInstanceId: 'flowNodeInstanceIdA',
          jobId: null,
        }),
      ];

      incidentsStore.setIncidents({incidents, count: 1});
      render(<IncidentsTable />, {wrapper: Wrapper});
      expect(
        screen.getByRole('button', {name: 'Sort by jobId'})
      ).toBeDisabled();
    });
  });

  describe('Selection', () => {
    it('should deselect selected incident', () => {
      const incidents = [createIncident()] as const;

      incidentsStore.setIncidents({incidents, count: 1});
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: incidents[0].flowNodeId,
        isMultiInstance: false,
      });

      render(<IncidentsTable />, {wrapper: Wrapper});
      expect(screen.getByRole('row', {selected: true})).toBeInTheDocument();

      userEvent.click(screen.getByRole('row', {selected: true}));
      expect(screen.getByRole('row', {selected: false})).toBeInTheDocument();
    });

    it('should select single incident when multiple incidents are selected', () => {
      const incidents = [
        createIncident({flowNodeId: 'myTask'}),
        createIncident({flowNodeId: 'myTask'}),
      ] as const;

      incidentsStore.setIncidents({incidents, count: 2});
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: incidents[0].flowNodeId,
        isMultiInstance: false,
      });

      render(<IncidentsTable />, {wrapper: Wrapper});
      expect(screen.getAllByRole('row', {selected: true})).toHaveLength(2);

      const [firstRow] = screen.getAllByRole('row', {
        name: 'Incident Condition error',
      });

      expect(firstRow).toBeInTheDocument();
      userEvent.click(firstRow!);

      expect(
        screen.getByRole('row', {
          name: 'Incident Condition error',
          selected: true,
        })
      ).toBeInTheDocument();
      expect(
        screen.getByRole('row', {
          name: 'Incident Condition error',
          selected: false,
        })
      ).toBeInTheDocument();
    });
  });
});
