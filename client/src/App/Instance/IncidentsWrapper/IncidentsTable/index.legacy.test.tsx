/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {IncidentsTable} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {createIncident, mockCallActivityProcessXML} from 'modules/testUtils';
import {formatDate} from 'modules/utils/date';
import {SORT_ORDER} from 'modules/constants';
import {Route, MemoryRouter} from 'react-router-dom';
import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {authenticationStore} from 'modules/stores/authentication';
import {IS_NEXT_INCIDENTS} from 'modules/feature-flags';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {mockServer} from 'modules/mock-server/node';
import {rest} from 'msw';

const id = 'flowNodeInstanceIdB';
const shortError = 'No data found for query $.orderId.';
const longError =
  'Cannot compare values of different types: INTEGER and BOOLEAN';
const mockProps = {
  incidents: [
    createIncident({
      errorType: 'Error A',
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
      errorType: 'Error B',
      errorMessage: longError,
      flowNodeId: 'Event_1db567d',
      flowNodeName: 'Event_1db567d',
      flowNodeInstanceId: id,
    }),
  ],
  onIncidentOperation: jest.fn(),
  onIncidentSelection: jest.fn(),
  selectedFlowNodeInstanceIds: [id],
  sorting: {
    sortBy: 'errorType',
    sortOrder: SORT_ORDER.DESC,
  },
  onSort: jest.fn(),
};

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/instances/1']}>
        <Route path="/instances/:processInstanceId">{children} </Route>
      </MemoryRouter>
    </ThemeProvider>
  );
};

(IS_NEXT_INCIDENTS ? describe.skip : describe)('IncidentsTable', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should render the right column headers', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockCallActivityProcessXML))
      )
    );
    await singleInstanceDiagramStore.fetchProcessXml('1');

    render(<IncidentsTable {...mockProps} />, {wrapper: Wrapper});

    expect(screen.getByText('Incident Type')).toBeInTheDocument();
    expect(screen.getByText('Flow Node')).toBeInTheDocument();
    expect(screen.getByText('Job Id')).toBeInTheDocument();
    expect(screen.getByText('Creation Time')).toBeInTheDocument();
    expect(screen.getByText('Error Message')).toBeInTheDocument();
    expect(screen.getByText('Operations')).toBeInTheDocument();
    if (IS_NEXT_INCIDENTS) {
      expect(screen.getByText('Root Cause Instance')).toBeInTheDocument();
    }
  });

  it('should render the right column headers for restricted user', () => {
    authenticationStore.setRoles(['view']);

    render(<IncidentsTable {...mockProps} />, {wrapper: Wrapper});

    expect(screen.getByText('Incident Type')).toBeInTheDocument();
    expect(screen.getByText('Flow Node')).toBeInTheDocument();
    expect(screen.getByText('Job Id')).toBeInTheDocument();
    expect(screen.getByText('Creation Time')).toBeInTheDocument();
    expect(screen.getByText('Error Message')).toBeInTheDocument();
    expect(screen.queryByText('Operations')).not.toBeInTheDocument();
    if (IS_NEXT_INCIDENTS) {
      expect(screen.getByText('Root Cause Instance')).toBeInTheDocument();
    }
  });

  it('should render incident details', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockCallActivityProcessXML))
      )
    );
    await singleInstanceDiagramStore.fetchProcessXml('1');

    render(<IncidentsTable {...mockProps} />, {wrapper: Wrapper});
    let withinRow = within(
      screen.getByTestId(`tr-incident-${mockProps.incidents[0].id}`)
    );

    expect(
      // @ts-expect-error
      withinRow.getByText(mockProps.incidents[0].errorType)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(mockProps.incidents[0].flowNodeName)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(mockProps.incidents[0].jobId)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(
        formatDate(mockProps.incidents[0].creationTime) || '--'
      )
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(mockProps.incidents[0].errorMessage)
    ).toBeInTheDocument();
    if (IS_NEXT_INCIDENTS) {
      expect(
        withinRow.getByRole('link', {
          name: /view root cause instance/i,
        })
      ).toBeInTheDocument();
      expect(
        withinRow.queryByRole('button', {name: 'Retry Incident'})
      ).not.toBeInTheDocument();
    } else {
      expect(
        withinRow.getByRole('button', {name: 'Retry Incident'})
      ).toBeInTheDocument();
    }
    withinRow = within(
      screen.getByTestId(`tr-incident-${mockProps.incidents[1].id}`)
    );
    expect(
      // @ts-expect-error
      withinRow.getByText(mockProps.incidents[1].errorType)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(mockProps.incidents[1].flowNodeName)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(mockProps.incidents[1].jobId)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(
        formatDate(mockProps.incidents[1].creationTime) || '--'
      )
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(mockProps.incidents[1].errorMessage)
    ).toBeInTheDocument();
    expect(
      withinRow.getByRole('button', {name: 'Retry Incident'})
    ).toBeInTheDocument();
  });

  it('should render incident details for restricted user', () => {
    authenticationStore.setRoles(['view']);

    render(<IncidentsTable {...mockProps} />, {wrapper: Wrapper});
    let withinRow = within(
      screen.getByTestId(`tr-incident-${mockProps.incidents[0].id}`)
    );

    expect(
      // @ts-expect-error
      withinRow.getByText(mockProps.incidents[0].errorType)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(mockProps.incidents[0].flowNodeName)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(mockProps.incidents[0].jobId)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(
        formatDate(mockProps.incidents[0].creationTime) || '--'
      )
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(mockProps.incidents[0].errorMessage)
    ).toBeInTheDocument();
    if (IS_NEXT_INCIDENTS) {
      expect(
        withinRow.getByRole('link', {
          name: /view root cause instance/i,
        })
      ).toBeInTheDocument();
    }
    expect(
      withinRow.queryByRole('button', {name: 'Retry Incident'})
    ).not.toBeInTheDocument();

    withinRow = within(
      screen.getByTestId(`tr-incident-${mockProps.incidents[1].id}`)
    );
    expect(
      // @ts-expect-error
      withinRow.getByText(mockProps.incidents[1].errorType)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(mockProps.incidents[1].flowNodeName)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(mockProps.incidents[1].jobId)
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(
        formatDate(mockProps.incidents[1].creationTime) || '--'
      )
    ).toBeInTheDocument();
    expect(
      withinRow.getByText(mockProps.incidents[1].errorMessage)
    ).toBeInTheDocument();
    expect(
      withinRow.queryByRole('button', {name: 'Retry Incident'})
    ).not.toBeInTheDocument();
    if (IS_NEXT_INCIDENTS) {
      expect(
        withinRow.queryByRole('link', {
          name: /view root cause instance/i,
        })
      ).not.toBeInTheDocument();
    }
  });

  it('should display -- for jobId', () => {
    const props = {
      ...mockProps,
      incidents: [
        createIncident({
          errorType: 'Error A',
          errorMessage: shortError,
          flowNodeName: 'Task A',
          flowNodeInstanceId: 'flowNodeInstanceIdA',
          jobId: null,
        }),
      ],
    };

    render(<IncidentsTable {...props} />, {wrapper: Wrapper});

    let withinFirstRow = within(
      screen.getByTestId(`tr-incident-${props.incidents[0].id}`)
    );

    expect(withinFirstRow.getByText('--')).toBeInTheDocument();
  });

  it('should show a more button for long error messages', () => {
    render(<IncidentsTable {...mockProps} />, {wrapper: Wrapper});
    let withinFirstRow = within(
      screen.getByTestId(`tr-incident-${mockProps.incidents[0].id}`)
    );

    expect(withinFirstRow.queryByText('More...')).not.toBeInTheDocument();

    let withinSecondRow = within(
      screen.getByTestId(`tr-incident-${mockProps.incidents[1].id}`)
    );

    expect(withinSecondRow.getByText('More...')).toBeInTheDocument();
  });

  it('should open an modal when clicking on the more button', () => {
    render(<IncidentsTable {...mockProps} />, {wrapper: Wrapper});

    let withinSecondRow = within(
      screen.getByTestId(`tr-incident-${mockProps.incidents[1].id}`)
    );

    expect(withinSecondRow.getByText('More...')).toBeInTheDocument();

    expect(screen.queryByTestId('modal')).not.toBeInTheDocument();

    userEvent.click(withinSecondRow.getByText('More...'));

    const modal = screen.getByTestId('modal');
    expect(
      within(modal).getByText(
        `Flow Node "${mockProps.incidents[1].flowNodeName}" Error`
      )
    ).toBeInTheDocument();
    expect(
      within(modal).getByText(mockProps.incidents[1].errorMessage)
    ).toBeInTheDocument();
  });

  describe('Sorting', () => {
    it('should enable sorting for all', () => {
      render(<IncidentsTable {...mockProps} />, {wrapper: Wrapper});

      expect(screen.getByText('Job Id')).toBeEnabled();
      expect(screen.getByText('Incident Type')).toBeEnabled();
      expect(screen.getByText('Flow Node')).toBeEnabled();
      expect(screen.getByText('Job Id')).toBeEnabled();
      expect(screen.getByText('Creation Time')).toBeEnabled();
      expect(screen.getByText('Error Message')).toBeEnabled();
      expect(screen.getByText('Operations')).toBeEnabled();
    });

    it('should disable sorting for jobId', () => {
      const props = {
        ...mockProps,
        incidents: [
          createIncident({
            errorType: 'Error A',
            errorMessage: shortError,
            flowNodeName: 'Task A',
            flowNodeInstanceId: 'flowNodeInstanceIdA',
            jobId: null,
          }),
        ],
      };

      render(<IncidentsTable {...props} />, {wrapper: Wrapper});
      expect(
        screen.getByRole('button', {name: 'Sort by jobId'})
      ).toBeDisabled();
    });
  });
});
