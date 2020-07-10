/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Router} from 'react-router-dom';
import {render, fireEvent, within, screen} from '@testing-library/react';
import {createMemoryHistory} from 'history';
import {InstancesByWorkflow} from './index';
import {
  mockWithSingleVersion,
  mockErrorResponse,
  mockEmptyResponse,
  mockWithMultipleVersions,
} from './index.setup';

const fetchMock = jest.spyOn(window, 'fetch');

const createWrapper = (historyMock = createMemoryHistory()) => ({children}) => (
  <Router history={historyMock}>{children}</Router>
);

describe('InstancesByWorkflow', () => {
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
          json: () => mockWithSingleVersion,
        })
      );

      render(<InstancesByWorkflow />, {
        wrapper: createWrapper(),
      });

      // display skeleton when loading
      expect(screen.getByTestId('skeleton')).toBeInTheDocument();

      // wait for request to be completed
      expect(
        await screen.findByTestId('instances-by-workflow')
      ).toBeInTheDocument();

      // remove skeleton when loaded
      expect(screen.queryByTestId('skeleton')).not.toBeInTheDocument();
    });

    it('should display error message when api fails', async () => {
      fetchMock.mockResolvedValueOnce(
        Promise.resolve({
          json: () => mockErrorResponse,
        })
      );

      render(<InstancesByWorkflow />, {
        wrapper: createWrapper(),
      });

      expect(
        await screen.findByText('Instances by Workflow could not be fetched.')
      ).toBeInTheDocument();
    });

    it('should display information message when there are no workflows', async () => {
      fetchMock.mockResolvedValueOnce(
        Promise.resolve({
          json: () => mockEmptyResponse,
        })
      );

      render(<InstancesByWorkflow />, {
        wrapper: createWrapper(),
      });

      expect(
        await screen.findByText('There are no Workflows.')
      ).toBeInTheDocument();
    });
  });

  describe('Content', () => {
    it('should render items with more than one workflows versions', async () => {
      fetchMock.mockResolvedValueOnce(
        Promise.resolve({
          json: () => mockWithMultipleVersions,
        })
      );

      const historyMock = createMemoryHistory();
      render(<InstancesByWorkflow />, {
        wrapper: createWrapper(historyMock),
      });

      const withinIncident = within(
        await screen.findByTestId('incident-byWorkflow-0')
      );

      const workflowLink = withinIncident.getByText(
        'Order process – 201 Instances in 2 Versions'
      );
      expect(workflowLink).toBeInTheDocument();
      fireEvent.click(workflowLink);
      expect(historyMock.location.search).toBe(
        '?filter={"workflow":"orderProcess","version":"all","incidents":true,"active":true}&name="Order process"'
      );

      expect(screen.getByTestId('incident-instances-badge')).toHaveTextContent(
        '65'
      );
      expect(screen.getByTestId('active-instances-badge')).toHaveTextContent(
        '136'
      );

      // click expand button to list versions
      const expandButton = withinIncident.getByTitle(
        'Expand 201 Instances of Workflow Order process'
      );

      expect(expandButton).toBeInTheDocument();
      fireEvent.click(expandButton);

      // contents of first version should be correct
      const firstVersion = screen.getByTitle(
        'View 42 Instances in Version 1 of Workflow First Version'
      );

      expect(
        within(firstVersion).getByTestId('incident-instances-badge')
      ).toHaveTextContent('37');
      expect(
        within(firstVersion).getByTestId('active-instances-badge')
      ).toHaveTextContent('5');
      expect(
        within(firstVersion).getByText(
          'First Version – 42 Instances in Version 1'
        )
      ).toBeInTheDocument();

      // the link of the first version should go to the correct route
      fireEvent.click(firstVersion);
      expect(historyMock.location.search).toBe(
        '?filter={"workflow":"mockWorkflow","version":"1","incidents":true,"active":true}&name="First Version"'
      );

      // contents of second version should be correct
      const secondVersion = screen.getByTitle(
        'View 42 Instances in Version 2 of Workflow Second Version'
      );

      expect(
        within(secondVersion).getByTestId('incident-instances-badge')
      ).toHaveTextContent('37');
      expect(
        within(secondVersion).getByTestId('active-instances-badge')
      ).toHaveTextContent('5');
      expect(
        within(secondVersion).getByText(
          'Second Version – 42 Instances in Version 2'
        )
      ).toBeInTheDocument();

      // the link of the second version should go to the correct route
      fireEvent.click(secondVersion);
      expect(historyMock.location.search).toBe(
        '?filter={"workflow":"mockWorkflow","version":"2","incidents":true,"active":true}&name="Second Version"'
      );
    });

    it('should render items with one workflow version', async () => {
      fetchMock.mockResolvedValueOnce(
        Promise.resolve({
          json: () => mockWithSingleVersion,
        })
      );

      const historyMock = createMemoryHistory();
      render(<InstancesByWorkflow />, {
        wrapper: createWrapper(historyMock),
      });

      const withinIncident = within(
        await screen.findByTestId('incident-byWorkflow-0')
      );

      expect(
        withinIncident.queryByTestId('expand-button')
      ).not.toBeInTheDocument();

      expect(
        withinIncident.getByText('loanProcess – 138 Instances in 1 Version')
      ).toBeInTheDocument();

      const workflowLink = withinIncident.getByTitle(
        'View 138 Instances in 1 Version of Workflow loanProcess'
      );
      expect(workflowLink).toBeInTheDocument();
      fireEvent.click(workflowLink);
      expect(historyMock.location.search).toBe(
        '?filter={"workflow":"loanProcess","version":"1","incidents":true,"active":true}&name="loanProcess"'
      );

      expect(screen.getByTestId('incident-instances-badge')).toHaveTextContent(
        '16'
      );
      expect(screen.getByTestId('active-instances-badge')).toHaveTextContent(
        '122'
      );
    });
  });
});
