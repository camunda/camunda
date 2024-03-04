/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
      tenants: [],
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
      tenants: [],
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
        name: new RegExp(firstIncident!.errorType.name),
      }),
    );

    expect(
      withinRow.getByText(firstIncident!.errorType.name),
    ).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.flowNodeId)).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.jobId!)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(firstIncident.creationTime) || '--'),
    ).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.errorMessage)).toBeInTheDocument();
    expect(
      withinRow.getByRole('link', {
        description: /view root cause instance/i,
      }),
    ).toBeInTheDocument();
    expect(
      withinRow.queryByRole('button', {name: 'Retry Incident'}),
    ).not.toBeInTheDocument();
    withinRow = within(
      screen.getByRole('row', {
        name: new RegExp(secondIncident!.errorType.name),
      }),
    );
    expect(
      withinRow.getByText(secondIncident.errorType.name),
    ).toBeInTheDocument();
    expect(withinRow.getByText(secondIncident.flowNodeId)).toBeInTheDocument();
    expect(withinRow.getByText(secondIncident.jobId!)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(secondIncident.creationTime) || '--'),
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(secondIncident.errorMessage),
    ).toBeInTheDocument();
    expect(
      withinRow.getByRole('button', {name: 'Retry Incident'}),
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
      tenants: [],
    });

    render(<IncidentsTable />, {wrapper: Wrapper});
    let withinRow = within(
      screen.getByRole('row', {
        name: new RegExp(firstIncident.errorType.name),
      }),
    );

    expect(
      withinRow.getByText(firstIncident.errorType.name),
    ).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.flowNodeId)).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.jobId!)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(firstIncident.creationTime) || '--'),
    ).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.errorMessage)).toBeInTheDocument();

    expect(
      withinRow.getByRole('link', {
        description: /view root cause instance/i,
      }),
    ).toBeInTheDocument();
    expect(
      withinRow.queryByRole('button', {name: 'Retry Incident'}),
    ).not.toBeInTheDocument();

    withinRow = within(
      screen.getByRole('row', {
        name: new RegExp(secondIncident.errorType.name),
      }),
    );
    expect(
      withinRow.getByText(secondIncident.errorType.name),
    ).toBeInTheDocument();
    expect(withinRow.getByText(secondIncident.flowNodeId)).toBeInTheDocument();
    expect(withinRow.getByText(secondIncident.jobId!)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(secondIncident.creationTime) || '--'),
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(secondIncident.errorMessage),
    ).toBeInTheDocument();
    expect(
      withinRow.queryByRole('button', {name: 'Retry Incident'}),
    ).not.toBeInTheDocument();
    expect(
      withinRow.queryByRole('link', {
        description: /view root cause instance/i,
      }),
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
      tenants: [],
    });

    render(<IncidentsTable />, {wrapper: Wrapper});
    let withinRow = within(
      screen.getByRole('row', {
        name: new RegExp(firstIncident.errorType.name),
      }),
    );

    expect(
      withinRow.getByText(firstIncident.errorType.name),
    ).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.flowNodeId)).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.jobId!)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(firstIncident.creationTime) || '--'),
    ).toBeInTheDocument();
    expect(withinRow.getByText(firstIncident.errorMessage)).toBeInTheDocument();

    expect(
      withinRow.getByRole('link', {
        description: /view root cause instance/i,
      }),
    ).toBeInTheDocument();
    expect(
      withinRow.queryByRole('button', {name: 'Retry Incident'}),
    ).not.toBeInTheDocument();

    withinRow = within(
      screen.getByRole('row', {
        name: new RegExp(secondIncident.errorType.name),
      }),
    );
    expect(
      withinRow.getByText(secondIncident.errorType.name),
    ).toBeInTheDocument();
    expect(withinRow.getByText(secondIncident.flowNodeId)).toBeInTheDocument();
    expect(withinRow.getByText(secondIncident.jobId!)).toBeInTheDocument();
    expect(
      withinRow.getByText(formatDate(secondIncident.creationTime) || '--'),
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(secondIncident.errorMessage),
    ).toBeInTheDocument();
    expect(
      withinRow.queryByRole('button', {name: 'Retry Incident'}),
    ).not.toBeInTheDocument();
    expect(
      withinRow.queryByRole('link', {
        description: /view root cause instance/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should display -- for jobId', () => {
    const incidentMock = {...firstIncident, jobId: null};
    const incidents = [incidentMock];

    incidentsStore.setIncidents({...incidentsMock, incidents, count: 1});

    render(<IncidentsTable />, {wrapper: Wrapper});

    let withinFirstRow = within(
      screen.getByRole('row', {
        name: new RegExp(incidentMock.errorType.name),
      }),
    );

    expect(withinFirstRow.getByText('--')).toBeInTheDocument();
  });

  it('should show a more button for long error messages', () => {
    incidentsStore.setIncidents(incidentsMock);
    render(<IncidentsTable />, {wrapper: Wrapper});
    let withinFirstRow = within(
      screen.getByRole('row', {
        name: new RegExp(firstIncident.errorType.name),
      }),
    );

    expect(withinFirstRow.queryByText('More')).not.toBeInTheDocument();

    let withinSecondRow = within(
      screen.getByRole('row', {
        name: new RegExp(secondIncident.errorType.name),
      }),
    );

    expect(withinSecondRow.getByText('More')).toBeInTheDocument();
  });

  it('should open an modal when clicking on the more button', async () => {
    incidentsStore.setIncidents(incidentsMock);
    const {user} = render(<IncidentsTable />, {wrapper: Wrapper});

    let withinSecondRow = within(
      screen.getByRole('row', {
        name: new RegExp(secondIncident.errorType.name),
      }),
    );

    expect(withinSecondRow.getByText('More')).toBeInTheDocument();

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await user.click(withinSecondRow.getByText('More'));

    const modal = screen.getByRole('dialog');

    expect(
      await within(modal).findByText(secondIncident.errorMessage),
    ).toBeInTheDocument();
    expect(
      within(modal).getByText(`Flow Node "${secondIncident.flowNodeId}" Error`),
    ).toBeInTheDocument();
  });
});
