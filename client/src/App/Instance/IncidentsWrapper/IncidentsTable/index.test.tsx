/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {IncidentsTable} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {createIncident} from 'modules/testUtils';
import {formatDate} from 'modules/utils/date';
import {SORT_ORDER} from 'modules/constants';
import {Route, MemoryRouter} from 'react-router-dom';
import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const id = 'flowNodeInstanceIdB';
const shortError = 'No data found for query $.orderId.';
const longError =
  'Cannot compare values of different types: INTEGER and BOOLEAN';
const mockProps = {
  incidents: [
    createIncident({
      errorType: 'Error A',
      errorMessage: shortError,
      flowNodeName: 'Task A',
      flowNodeInstanceId: 'flowNodeInstanceIdA',
    }),
    createIncident({
      errorType: 'Error B',
      errorMessage: longError,
      flowNodeName: 'Task B',
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

describe('IncidentsTable', () => {
  it('should render the right column headers', () => {
    render(<IncidentsTable {...mockProps} />, {wrapper: Wrapper});

    expect(screen.getByText('Incident Type')).toBeInTheDocument();
    expect(screen.getByText('Flow Node')).toBeInTheDocument();
    expect(screen.getByText('Job Id')).toBeInTheDocument();
    expect(screen.getByText('Creation Time')).toBeInTheDocument();
    expect(screen.getByText('Error Message')).toBeInTheDocument();
    expect(screen.getByText('Operations')).toBeInTheDocument();
  });

  it('should render incident details', () => {
    render(<IncidentsTable {...mockProps} />, {wrapper: Wrapper});
    let withinRow = within(
      screen.getByTestId(`tr-incident-${mockProps.incidents[0].id}`)
    );

    expect(
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

    withinRow = within(
      screen.getByTestId(`tr-incident-${mockProps.incidents[1].id}`)
    );
    expect(
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
