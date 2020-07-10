/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Router} from 'react-router-dom';
import {
  render,
  fireEvent,
  within,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {createMemoryHistory} from 'history';
import {IncidentsByError} from './index';
import {
  mockIncidentsByError,
  mockErrorResponse,
  mockEmptyResponse,
} from './index.setup';

const fetchMock = jest.spyOn(window, 'fetch');

const createWrapper = (historyMock = createMemoryHistory()) => ({children}) => (
  <Router history={historyMock}>{children}</Router>
);

describe('IncidentsByError', () => {
  afterEach(() => {
    fetchMock.mockClear();
  });

  afterAll(() => {
    fetchMock.mockRestore();
  });

  describe('Empty Panel', () => {
    it('should display skeleton when loading', async () => {
      fetchMock.mockResolvedValueOnce(
        Promise.resolve({
          json: () => mockIncidentsByError,
        })
      );

      render(<IncidentsByError />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByTestId('skeleton')).toBeInTheDocument();

      await waitForElementToBeRemoved(screen.getByTestId('skeleton'));
    });

    it('should display error message when api fails', async () => {
      fetchMock.mockResolvedValueOnce(
        Promise.resolve({
          json: () => mockErrorResponse,
        })
      );

      render(<IncidentsByError />, {
        wrapper: createWrapper(),
      });

      expect(
        await screen.findByText(
          'Incidents by Error Message could not be fetched.'
        )
      ).toBeInTheDocument();
    });

    it('should display information message when there are no workflows', async () => {
      fetchMock.mockResolvedValueOnce(
        Promise.resolve({
          json: () => mockEmptyResponse,
        })
      );

      render(<IncidentsByError />, {
        wrapper: createWrapper(),
      });

      expect(
        await screen.findByText('There are no Instances with Incident.')
      ).toBeInTheDocument();
    });
  });

  describe('Content', () => {
    it('should render incidents by error message', async () => {
      fetchMock.mockResolvedValueOnce(
        Promise.resolve({
          json: () => mockIncidentsByError,
        })
      );

      const historyMock = createMemoryHistory();
      render(<IncidentsByError />, {
        wrapper: createWrapper(historyMock),
      });

      const withinIncident = within(
        await screen.findByTestId('incident-byError-0')
      );

      const expandButton = withinIncident.getByTitle(
        "Expand 36 Instances with error JSON path '$.paid' has no result."
      );
      expect(expandButton).toBeInTheDocument();

      fireEvent.click(
        withinIncident.getByTitle(
          "View 36 Instances with error JSON path '$.paid' has no result."
        )
      );
      expect(historyMock.location.search).toBe(
        '?filter={"errorMessage":"JSON path \'$.paid\' has no result.","incidents":true}'
      );

      fireEvent.click(expandButton);

      const firstVersion = withinIncident.getByTitle(
        "View 37 Instances with error JSON path '$.paid' has no result. in version 1 of Workflow mockWorkflow"
      );
      expect(
        within(firstVersion).getByTestId('incident-instances-badge')
      ).toHaveTextContent('37');
      expect(
        within(firstVersion).getByText('mockWorkflow – Version 1')
      ).toBeInTheDocument();

      fireEvent.click(firstVersion);
      expect(historyMock.location.search).toBe(
        '?filter={"workflow":"mockWorkflow","version":"1","errorMessage":"JSON path \'$.paid\' has no result.","incidents":true}'
      );
    });
  });
});
