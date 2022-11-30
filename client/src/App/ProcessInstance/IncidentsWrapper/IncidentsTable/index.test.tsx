/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {IncidentsTable} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {createIncident, mockCallActivityProcessXML} from 'modules/testUtils';
import {formatDate} from 'modules/utils/date';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {render, screen, within} from 'modules/testing-library';
import {authenticationStore} from 'modules/stores/authentication';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

const id = 'flowNodeInstanceIdB';
const shortError = 'No data found for query $.orderId.';
const longError =
  'Cannot compare values of different types: INTEGER and BOOLEAN';

const firstIncident = createIncident({
  errorType: {name: 'Error A', id: 'ERROR_A'},
  errorMessage: shortError,
  flowNodeId: 'StartEvent_1',
  flowNodeInstanceId: '18239123812938',
  rootCauseInstance: {
    instanceId: '111111111111111111',
    processDefinitionId: 'calledInstance',
    processDefinitionName: 'Called Instance',
  },
});

const secondIncident = createIncident({
  errorType: {name: 'Error B', id: 'ERROR_A'},
  errorMessage: longError,
  flowNodeId: 'Event_1db567d',
  flowNodeInstanceId: id,
});

const incidentsMock = {
  incidents: [firstIncident, secondIncident],
  count: 2,
  errorTypes: [],
  flowNodes: [],
};

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
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

describe('IncidentsTable', () => {
  afterEach(() => {
    incidentsStore.reset();
    authenticationStore.reset();
    flowNodeSelectionStore.reset();
  });

  it('should render the right column headers', async () => {
    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);

    incidentsStore.setIncidents(incidentsMock);

    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    render(<IncidentsTable />, {wrapper: Wrapper});

    expect(screen.getByText('Incident Type')).toBeInTheDocument();
    expect(screen.getByText('Failing Flow Node')).toBeInTheDocument();
    expect(screen.getByText('Job Id')).toBeInTheDocument();
    expect(screen.getByText('Creation Date')).toBeInTheDocument();
    expect(screen.getByText('Error Message')).toBeInTheDocument();
    expect(screen.getByText('Operations')).toBeInTheDocument();
    expect(screen.getByText('Root Cause Instance')).toBeInTheDocument();
  });

  it('should render the right column headers for restricted user', () => {
    incidentsStore.setIncidents(incidentsMock);
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(<IncidentsTable />, {wrapper: Wrapper});

    expect(screen.getByText('Incident Type')).toBeInTheDocument();
    expect(screen.getByText('Failing Flow Node')).toBeInTheDocument();
    expect(screen.getByText('Job Id')).toBeInTheDocument();
    expect(screen.getByText('Creation Date')).toBeInTheDocument();
    expect(screen.getByText('Error Message')).toBeInTheDocument();
    expect(screen.queryByText('Operations')).not.toBeInTheDocument();
    expect(screen.getByText('Root Cause Instance')).toBeInTheDocument();
  });

  it('should render incident details', async () => {
    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);

    incidentsStore.setIncidents(incidentsMock);
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    render(<IncidentsTable />, {wrapper: Wrapper});
    let withinRow = within(
      screen.getByRole('row', {
        name: `Incident ${firstIncident!.errorType.name}`,
      })
    );

    expect(
      withinRow.getByText(firstIncident!.errorType.name)
    ).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.flowNodeId)).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.jobId!)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(firstIncident.creationTime) || '--')
    ).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.errorMessage)).toBeInTheDocument();
    expect(
      withinRow.getByRole('link', {
        name: /view root cause instance/i,
      })
    ).toBeInTheDocument();
    expect(
      withinRow.queryByRole('button', {name: 'Retry Incident'})
    ).not.toBeInTheDocument();
    withinRow = within(
      screen.getByRole('row', {
        name: `Incident ${secondIncident.errorType.name}`,
      })
    );
    expect(
      withinRow.getByText(secondIncident.errorType.name)
    ).toBeInTheDocument();
    expect(withinRow.getByText(secondIncident.flowNodeId)).toBeInTheDocument();
    expect(withinRow.getByText(secondIncident.jobId!)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(secondIncident.creationTime) || '--')
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(secondIncident.errorMessage)
    ).toBeInTheDocument();
    expect(
      withinRow.getByRole('button', {name: 'Retry Incident'})
    ).toBeInTheDocument();
  });

  it('should render incident details for restricted user', () => {
    incidentsStore.setIncidents(incidentsMock);
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(<IncidentsTable />, {wrapper: Wrapper});
    let withinRow = within(
      screen.getByRole('row', {
        name: `Incident ${firstIncident.errorType.name}`,
      })
    );

    expect(
      withinRow.getByText(firstIncident.errorType.name)
    ).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.flowNodeId)).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.jobId!)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(firstIncident.creationTime) || '--')
    ).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.errorMessage)).toBeInTheDocument();

    expect(
      withinRow.getByRole('link', {
        name: /view root cause instance/i,
      })
    ).toBeInTheDocument();
    expect(
      withinRow.queryByRole('button', {name: 'Retry Incident'})
    ).not.toBeInTheDocument();

    withinRow = within(
      screen.getByRole('row', {
        name: `Incident ${secondIncident.errorType.name}`,
      })
    );
    expect(
      withinRow.getByText(secondIncident.errorType.name)
    ).toBeInTheDocument();
    expect(withinRow.getByText(secondIncident.flowNodeId)).toBeInTheDocument();
    expect(withinRow.getByText(secondIncident.jobId!)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(secondIncident.creationTime) || '--')
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(secondIncident.errorMessage)
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
    const incidentMock = {...firstIncident, jobId: null};
    const incidents = [incidentMock];

    incidentsStore.setIncidents({...incidentsMock, incidents, count: 1});

    render(<IncidentsTable />, {wrapper: Wrapper});

    let withinFirstRow = within(
      screen.getByRole('row', {
        name: `Incident ${incidentMock.errorType.name}`,
      })
    );

    expect(withinFirstRow.getByText('--')).toBeInTheDocument();
  });

  it('should show a more button for long error messages', () => {
    incidentsStore.setIncidents(incidentsMock);
    render(<IncidentsTable />, {wrapper: Wrapper});
    let withinFirstRow = within(
      screen.getByRole('row', {
        name: `Incident ${firstIncident.errorType.name}`,
      })
    );

    expect(withinFirstRow.queryByText('More...')).not.toBeInTheDocument();

    let withinSecondRow = within(
      screen.getByRole('row', {
        name: `Incident ${secondIncident.errorType.name}`,
      })
    );

    expect(withinSecondRow.getByText('More...')).toBeInTheDocument();
  });

  it('should open an modal when clicking on the more button', async () => {
    incidentsStore.setIncidents(incidentsMock);
    const {user} = render(<IncidentsTable />, {wrapper: Wrapper});

    let withinSecondRow = within(
      screen.getByRole('row', {
        name: `Incident ${secondIncident.errorType.name}`,
      })
    );

    expect(withinSecondRow.getByText('More...')).toBeInTheDocument();

    expect(screen.queryByTestId('modal')).not.toBeInTheDocument();

    await user.click(withinSecondRow.getByText('More...'));

    const modal = screen.getByTestId('modal');
    expect(
      within(modal).getByText(`Flow Node "${secondIncident.flowNodeId}" Error`)
    ).toBeInTheDocument();
    expect(
      within(modal).getByText(secondIncident.errorMessage)
    ).toBeInTheDocument();
  });

  describe('Sorting', () => {
    it('should enable sorting for all', () => {
      incidentsStore.setIncidents(incidentsMock);
      render(<IncidentsTable />, {wrapper: Wrapper});

      expect(screen.getByText('Job Id')).toBeEnabled();
      expect(screen.getByText('Incident Type')).toBeEnabled();
      expect(screen.getByText('Failing Flow Node')).toBeEnabled();
      expect(screen.getByText('Job Id')).toBeEnabled();
      expect(screen.getByText('Creation Date')).toBeEnabled();
      expect(screen.getByText('Error Message')).toBeEnabled();
      expect(screen.getByText('Operations')).toBeEnabled();
    });

    it('should disable sorting for jobId', () => {
      const incidents = [
        createIncident({
          errorType: {
            name: 'Error A',
            id: 'ERROR-A',
          },
          errorMessage: shortError,
          flowNodeId: 'Task A',
          flowNodeInstanceId: 'flowNodeInstanceIdA',
          jobId: null,
        }),
      ];

      incidentsStore.setIncidents({...incidentsMock, incidents, count: 1});

      render(<IncidentsTable />, {wrapper: Wrapper});
      expect(
        screen.getByRole('button', {name: 'Sort by Job Id'})
      ).toBeDisabled();
    });
  });

  describe('Selection', () => {
    it('should deselect selected incident', async () => {
      incidentsStore.setIncidents({
        ...incidentsMock,
        incidents: [firstIncident],
        count: 1,
      });
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: firstIncident.flowNodeId,
        isMultiInstance: false,
      });

      const {user} = render(<IncidentsTable />, {wrapper: Wrapper});
      expect(screen.getByRole('row', {selected: true})).toBeInTheDocument();

      await user.click(screen.getByRole('row', {selected: true}));
      expect(screen.getByRole('row', {selected: false})).toBeInTheDocument();
    });

    it('should select single incident when multiple incidents are selected', async () => {
      const incidents = [
        createIncident({flowNodeId: 'myTask'}),
        createIncident({flowNodeId: 'myTask'}),
      ];

      incidentsStore.setIncidents({...incidentsMock, incidents});
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'myTask',
        isMultiInstance: false,
      });

      const {user} = render(<IncidentsTable />, {wrapper: Wrapper});
      expect(screen.getAllByRole('row', {selected: true})).toHaveLength(2);

      const [firstRow] = screen.getAllByRole('row', {
        name: 'Incident Condition error',
      });

      expect(firstRow).toBeInTheDocument();
      await user.click(firstRow!);

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
