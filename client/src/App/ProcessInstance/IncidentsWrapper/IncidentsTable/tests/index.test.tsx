/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {IncidentsTable} from '../index';
import {mockCallActivityProcessXML} from 'modules/testUtils';
import {formatDate} from 'modules/utils/date';
import {render, screen, within} from 'modules/testing-library';
import {authenticationStore} from 'modules/stores/authentication';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {incidentsStore} from 'modules/stores/incidents';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {Wrapper, incidentsMock, firstIncident, secondIncident} from './mocks';

describe('IncidentsTable', () => {
  afterEach(() => {
    window.clientConfig = undefined;
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

  it('should render the right column headers for restricted user', async () => {
    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);

    incidentsStore.setIncidents(incidentsMock);
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');
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

  it('should render the right column headers for restricted user (with resource-based permissions)', async () => {
    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };

    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);

    incidentsStore.setIncidents(incidentsMock);
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['write'],
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
        description: /view root cause instance/i,
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

  it('should render incident details for restricted user', async () => {
    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

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
        description: /view root cause instance/i,
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
        description: /view root cause instance/i,
      })
    ).not.toBeInTheDocument();
  });

  it('should render incident details for restricted user (with resource-based permissions)', async () => {
    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };

    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    incidentsStore.setIncidents(incidentsMock);
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['write'],
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
        description: /view root cause instance/i,
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
        description: /view root cause instance/i,
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
      await within(modal).findByText(secondIncident.errorMessage)
    ).toBeInTheDocument();
    expect(
      within(modal).getByText(`Flow Node "${secondIncident.flowNodeId}" Error`)
    ).toBeInTheDocument();
  });
});
