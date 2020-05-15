/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import IncidentsTable from './IncidentsTable';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {createIncident} from 'modules/testUtils';
import {formatDate} from 'modules/utils/date';
import {SORT_ORDER} from 'modules/constants';
import {DataManagerProvider} from 'modules/DataManager';
import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {Router, Route} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {render, screen, within, fireEvent} from '@testing-library/react';

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

const renderComponent = (mockProps) =>
  render(
    <ThemeProvider>
      <DataManagerProvider>
        <Router
          history={createMemoryHistory({initialEntries: ['/instances/1']})}
        >
          <Route path="/instances/:id">
            <IncidentsTable {...mockProps} />
          </Route>
        </Router>
      </DataManagerProvider>
    </ThemeProvider>
  );

describe('IncidentsTable', () => {
  beforeEach(() => {
    createMockDataManager();
  });

  it('should render the right column headers', () => {
    renderComponent(mockProps);
    expect(screen.getByText('Incident Type')).toBeInTheDocument();
    expect(screen.getByText('Flow Node')).toBeInTheDocument();
    expect(screen.getByText('Job Id')).toBeInTheDocument();
    expect(screen.getByText('Creation Time')).toBeInTheDocument();
    expect(screen.getByText('Error Message')).toBeInTheDocument();
    expect(screen.getByText('Operations')).toBeInTheDocument();
  });

  it('should render incident details', () => {
    renderComponent(mockProps);
    let row = screen.getByTestId(`tr-${mockProps.incidents[0].id}`);
    expect(
      within(row).getByText(mockProps.incidents[0].errorType)
    ).toBeInTheDocument();
    expect(
      within(row).getByText(mockProps.incidents[0].flowNodeName)
    ).toBeInTheDocument();
    expect(
      within(row).getByText(mockProps.incidents[0].jobId)
    ).toBeInTheDocument();
    expect(
      within(row).getByText(formatDate(mockProps.incidents[0].creationTime))
    ).toBeInTheDocument();
    expect(
      within(row).getByText(mockProps.incidents[0].errorMessage)
    ).toBeInTheDocument();

    row = screen.getByTestId(`tr-${mockProps.incidents[1].id}`);
    expect(
      within(row).getByText(mockProps.incidents[1].errorType)
    ).toBeInTheDocument();
    expect(
      within(row).getByText(mockProps.incidents[1].flowNodeName)
    ).toBeInTheDocument();
    expect(
      within(row).getByText(mockProps.incidents[1].jobId)
    ).toBeInTheDocument();
    expect(
      within(row).getByText(formatDate(mockProps.incidents[1].creationTime))
    ).toBeInTheDocument();
    expect(
      within(row).getByText(mockProps.incidents[1].errorMessage)
    ).toBeInTheDocument();
  });

  it('should display -- for jobId', () => {
    let props = {...mockProps};
    props.incidents = [
      createIncident({
        errorType: 'Error A',
        errorMessage: shortError,
        flowNodeName: 'Task A',
        flowNodeInstanceId: 'flowNodeInstanceIdA',
        jobId: null,
      }),
    ];
    renderComponent(props);
    let row = screen.getByTestId(`tr-${props.incidents[0].id}`);
    expect(within(row).getByText('--')).toBeInTheDocument();
  });

  it('should show a more button for long error messages', () => {
    renderComponent(mockProps);

    let firstRow = screen.getByTestId(`tr-${mockProps.incidents[0].id}`);
    expect(within(firstRow).queryByText('More...')).not.toBeInTheDocument();

    let secondRow = screen.getByTestId(`tr-${mockProps.incidents[1].id}`);
    expect(within(secondRow).getByText('More...')).toBeInTheDocument();
  });

  it('should open an modal when clicking on the more button', () => {
    renderComponent(mockProps);

    let secondRow = screen.getByTestId(`tr-${mockProps.incidents[1].id}`);
    expect(within(secondRow).getByText('More...')).toBeInTheDocument();

    expect(screen.queryByTestId('modal')).not.toBeInTheDocument();

    fireEvent.click(within(secondRow).getByText('More...'));

    const modal = screen.getByTestId('modal');
    expect(modal).toBeInTheDocument();
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
      renderComponent(mockProps);

      expect(screen.getByText('Job Id')).not.toHaveAttribute('disabled');
      expect(screen.getByText('Incident Type')).not.toHaveAttribute('disabled');
      expect(screen.getByText('Flow Node')).not.toHaveAttribute('disabled');
      expect(screen.getByText('Job Id')).not.toHaveAttribute('disabled');
      expect(screen.getByText('Creation Time')).not.toHaveAttribute('disabled');
      expect(screen.getByText('Error Message')).not.toHaveAttribute('disabled');
      expect(screen.getByText('Operations')).not.toHaveAttribute('disabled');
    });
    it('should disable sorting for jobId', () => {
      let props = {...mockProps};
      props.incidents = [
        createIncident({
          errorType: 'Error A',
          errorMessage: shortError,
          flowNodeName: 'Task A',
          flowNodeInstanceId: 'flowNodeInstanceIdA',
          jobId: null,
        }),
      ];

      renderComponent(props);

      expect(screen.getByText('Job Id')).toHaveAttribute('disabled');
    });
  });
});
