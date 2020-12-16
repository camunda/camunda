/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// TODO (paddy): rename to index.test.tsx, when FlowNodeInstancesTree is done

import React from 'react';
import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import {rest} from 'msw';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {FlowNodeInstancesTree} from './index.next';
import {mockServer} from 'modules/mockServer';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {
  flowNodeInstanceStore,
  FlowNodeInstance,
} from 'modules/stores/flowNodeInstance';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';

import {CURRENT_INSTANCE} from './index.setup';
import {
  createMultiInstanceFlowNodeInstances,
  multiInstanceWorkflow,
} from 'modules/testUtils';

const workflowId = '1';
const workflowInstanceId = CURRENT_INSTANCE.id;

const flowNodeInstances = createMultiInstanceFlowNodeInstances(
  workflowInstanceId
);

const mockFlowNodeInstance: FlowNodeInstance = {
  id: workflowInstanceId,
  type: 'WORKFLOW',
  state: 'COMPLETED',
  flowNodeId: workflowId,
  treePath: workflowInstanceId,
  startDate: '',
  endDate: null,
  sortValues: [],
};

describe('<FlowNodeInstancesTree />', () => {
  beforeEach(async () => {
    mockServer.use(
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(flowNodeInstances.level1))
      ),
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(flowNodeInstances.level2))
      ),
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(flowNodeInstances.level3))
      ),
      rest.get(`/api/workflow-instances/:workflowInstanceId`, (_, res, ctx) =>
        res.once(ctx.json(CURRENT_INSTANCE))
      ),
      rest.get(`/api/workflows/:workflowId/xml`, (_, res, ctx) =>
        res.once(ctx.text(multiInstanceWorkflow))
      )
    );

    await Promise.all([
      flowNodeInstanceStore.init(),
      currentInstanceStore.init(workflowInstanceId),
      singleInstanceDiagramStore.fetchWorkflowXml(workflowId),
    ]);
  });

  afterEach(() => {
    currentInstanceStore.reset();
    singleInstanceDiagramStore.reset();
    flowNodeInstanceStore.reset();
  });

  it('should load the instance history', async () => {
    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    render(
      // @ts-expect-error
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={mockFlowNodeInstance}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.getByText('Multi-Instance Process')).toBeInTheDocument();
    expect(screen.getByText('Peter Fork')).toBeInTheDocument();
    expect(
      screen.getByText('Filter-Map Sub Process (Multi Instance)')
    ).toBeInTheDocument();
  });

  it('should be able to unfold and fold subprocesses', async () => {
    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    render(
      // @ts-expect-error
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={mockFlowNodeInstance}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.queryByRole('button', {
        name: 'Unfold Filter-Map Sub Process',
      })
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Unfold Filter-Map Sub Process (Multi Instance)',
      })
    );

    expect(
      await screen.findByRole('button', {
        name: 'Fold Filter-Map Sub Process (Multi Instance)',
      })
    ).toBeInTheDocument();
    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();

    fireEvent.click(
      await screen.findByRole('button', {
        name: 'Unfold Filter-Map Sub Process',
      })
    );

    expect(await screen.findByText('Start Filter-Map')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: 'Fold Filter-Map Sub Process (Multi Instance)',
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: 'Fold Filter-Map Sub Process',
      })
    ).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Fold Filter-Map Sub Process',
      })
    );

    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();
  });
});
