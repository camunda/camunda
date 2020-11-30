/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen, waitFor} from '@testing-library/react';
import {rest} from 'msw';
import {createMemoryHistory} from 'history';
import {filtersStore} from 'modules/stores/filters';
import {instancesStore} from 'modules/stores/instances';
import {Operations} from './index';
import {mockServer} from 'modules/mockServer';
import {INSTANCE, ACTIVE_INSTANCE} from './index.setup';
import {groupedWorkflowsMock} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

const instanceMock: InstanceEntity = {
  id: 'instance_1',
  state: 'ACTIVE',
  operations: [],
  bpmnProcessId: '',
  startDate: '',
  endDate: null,
  hasActiveOperation: true,
  workflowId: '',
  workflowName: '',
  workflowVersion: 1,
};

describe('Operations', () => {
  describe('Operation Buttons', () => {
    it('should render retry and cancel button if instance is running and has an incident', () => {
      render(<Operations instance={{...instanceMock, state: 'INCIDENT'}} />, {
        wrapper: ThemeProvider,
      });

      expect(
        screen.getByTitle(`Retry Instance instance_1`)
      ).toBeInTheDocument();
      expect(
        screen.getByTitle(`Cancel Instance instance_1`)
      ).toBeInTheDocument();
    });
    it('should render only cancel button if instance is running and does not have an incident', () => {
      render(<Operations instance={{...instanceMock, state: 'ACTIVE'}} />, {
        wrapper: ThemeProvider,
      });

      expect(
        screen.queryByTitle(`Retry Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.getByTitle(`Cancel Instance instance_1`)
      ).toBeInTheDocument();
    });
    it('should not render retry and cancel buttons if instance is completed', () => {
      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'COMPLETED',
          }}
        />,
        {wrapper: ThemeProvider}
      );

      expect(
        screen.queryByTitle(`Retry Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTitle(`Cancel Instance instance_1`)
      ).not.toBeInTheDocument();
    });
    it('should not render retry and cancel buttons if instance is canceled', () => {
      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'CANCELED',
          }}
        />,
        {wrapper: ThemeProvider}
      );

      expect(
        screen.queryByTitle(`Retry Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTitle(`Cancel Instance instance_1`)
      ).not.toBeInTheDocument();
    });
  });
  describe('Spinner', () => {
    it('should not display spinner', () => {
      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'INCIDENT',
          }}
        />,
        {wrapper: ThemeProvider}
      );

      expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
    });
    it('should display spinner if it is forced', () => {
      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'INCIDENT',
          }}
          forceSpinner={true}
        />,
        {wrapper: ThemeProvider}
      );

      expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    });

    it('should display spinner if incident id is included in instances with active operations', async () => {
      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(
              ctx.json({workflowInstances: [ACTIVE_INSTANCE], totalCount: 1})
            )
        ),
        rest.get('/api/workflows/grouped', (_, res, ctx) =>
          res.once(ctx.json(groupedWorkflowsMock))
        )
      );

      filtersStore.setUrlParameters(createMemoryHistory(), {
        pathname: '/instances',
      });
      await filtersStore.init();

      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'INCIDENT',
          }}
        />,
        {wrapper: ThemeProvider}
      );

      expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

      jest.useFakeTimers();
      instancesStore.init();

      await waitFor(() =>
        expect(screen.getByTestId('operation-spinner')).toBeInTheDocument()
      );

      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(ctx.json({workflowInstances: [INSTANCE], totalCount: 1}))
        )
      );

      jest.runOnlyPendingTimers();

      await waitFor(() =>
        expect(
          screen.queryByTestId('operation-spinner')
        ).not.toBeInTheDocument()
      );

      instancesStore.reset();
      filtersStore.reset();

      jest.useRealTimers();
    });
  });
});
