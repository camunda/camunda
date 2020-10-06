/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {STATE, OPERATION_STATE} from 'modules/constants';
import {filters} from 'modules/stores/filters';
import {instances} from 'modules/stores/instances';
import {Operations} from './index';
import {
  render,
  screen,
  fireEvent,
  waitFor,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {INSTANCE, ACTIVE_INSTANCE, mockOperationCreated} from './index.setup';
import {createMemoryHistory} from 'history';
import {groupedWorkflowsMock} from 'modules/testUtils';

describe('Operations', () => {
  describe('Operation Buttons', () => {
    it('should render retry and cancel button if instance is running and has an incident', () => {
      render(
        <Operations
          instance={{id: 'instance_1', state: STATE.INCIDENT, operations: []}}
        />
      );

      expect(
        screen.getByTitle(`Retry Instance instance_1`)
      ).toBeInTheDocument();
      expect(
        screen.getByTitle(`Cancel Instance instance_1`)
      ).toBeInTheDocument();
    });
    it('should render only cancel button if instance is running and does not have an incident', () => {
      render(
        <Operations
          instance={{id: 'instance_1', state: STATE.ACTIVE, operations: []}}
        />
      );

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
          instance={{id: 'instance_1', state: STATE.COMPLETED, operations: []}}
        />
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
          instance={{id: 'instance_1', state: STATE.COMPLETED, operations: []}}
        />
      );

      expect(
        screen.queryByTitle(`Retry Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTitle(`Cancel Instance instance_1`)
      ).not.toBeInTheDocument();
    });

    it('should display spinner after clicking retry button', () => {
      mockServer.use(
        rest.post(
          '/api/workflow-instances/:instanceId/operation',
          (_, res, ctx) => res.once(ctx.json(mockOperationCreated))
        )
      );

      render(
        <Operations
          instance={{id: 'instance_1', state: STATE.INCIDENT, operations: []}}
          onButtonClick={jest.fn()}
        />
      );

      expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
      fireEvent.click(screen.getByTitle(`Retry Instance instance_1`));
      expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    });

    it('should display spinner after clicking cancel button', () => {
      mockServer.use(
        rest.post(
          '/api/workflow-instances/:instanceId/operation',
          (_, res, ctx) => res.once(ctx.json(mockOperationCreated))
        )
      );

      render(
        <Operations
          instance={{id: 'instance_1', state: STATE.INCIDENT, operations: []}}
          onButtonClick={jest.fn()}
        />
      );

      expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
      fireEvent.click(screen.getByTitle(`Cancel Instance instance_1`));
      expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    });
  });
  describe('Spinner', () => {
    it('should not display spinner', () => {
      render(
        <Operations
          instance={{id: 'instance_1', state: STATE.INCIDENT, operations: []}}
        />
      );

      expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
    });
    it('should display spinner if it is forced', () => {
      render(
        <Operations
          instance={{id: 'instance_1', state: STATE.INCIDENT, operations: []}}
          forceSpinner={true}
        />
      );

      expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    });

    it("should display spinner if incident's latest operation is scheduled, locked or sent", () => {
      const {rerender} = render(
        <Operations
          instance={{
            id: 'instance_1',
            state: STATE.INCIDENT,
            operations: [{type: 'Retry', state: OPERATION_STATE.SCHEDULED}],
          }}
        />
      );

      expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

      rerender(
        <Operations
          instance={{
            id: 'instance_1',
            state: STATE.INCIDENT,
            operations: [{type: 'Retry', state: OPERATION_STATE.LOCKED}],
          }}
        />
      );
      expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

      rerender(
        <Operations
          instance={{
            id: 'instance_1',
            state: STATE.INCIDENT,
            operations: [{type: 'Retry', state: OPERATION_STATE.SENT}],
          }}
        />
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

      filters.setUrlParameters(createMemoryHistory(), {pathname: '/instances'});
      await filters.init();

      render(
        <Operations
          instance={{
            id: 'instance_1',
            state: STATE.INCIDENT,
            operations: [],
          }}
        />
      );

      expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

      jest.useFakeTimers();
      instances.init();

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

      instances.reset();
      filters.reset();

      jest.useRealTimers();
    });
  });
});
