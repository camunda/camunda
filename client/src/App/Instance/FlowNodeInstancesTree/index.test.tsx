/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {rest} from 'msw';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {mockServer} from 'modules/mock-server/node';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {
  flowNodeInstanceStore,
  FlowNodeInstance,
} from 'modules/stores/flowNodeInstance';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {FlowNodeInstancesTree} from './index';

import {CURRENT_INSTANCE} from './index.setup';
import {
  createMultiInstanceFlowNodeInstances,
  multiInstanceProcess,
} from 'modules/testUtils';

const processId = '1';
const processInstanceId = CURRENT_INSTANCE.id;

const flowNodeInstances =
  createMultiInstanceFlowNodeInstances(processInstanceId);

const mockFlowNodeInstance: FlowNodeInstance = {
  id: processInstanceId,
  type: 'PROCESS',
  state: 'COMPLETED',
  flowNodeId: processId,
  treePath: processInstanceId,
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
      rest.get(`/api/process-instances/:processInstanceId`, (_, res, ctx) =>
        res.once(ctx.json(CURRENT_INSTANCE))
      ),
      rest.get(`/api/processes/:processId/xml`, (_, res, ctx) =>
        res.once(ctx.text(multiInstanceProcess))
      )
    );
    await singleInstanceDiagramStore.fetchProcessXml(processId);

    jest.useFakeTimers();

    currentInstanceStore.init(processInstanceId);
    flowNodeInstanceStore.init();
  });

  afterEach(() => {
    currentInstanceStore.reset();
    singleInstanceDiagramStore.reset();
    flowNodeInstanceStore.reset();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should load the instance history', async () => {
    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    render(
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={mockFlowNodeInstance}
        isLastChild={false}
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

    mockServer.use(
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(flowNodeInstances.level2))
      ),
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(flowNodeInstances.level3))
      )
    );

    render(
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={mockFlowNodeInstance}
        isLastChild={false}
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

    userEvent.click(
      screen.getByRole('button', {
        name: 'Unfold Filter-Map Sub Process (Multi Instance)',
      })
    );

    expect(
      await screen.findByRole(
        'button',
        {
          name: 'Fold Filter-Map Sub Process (Multi Instance)',
        },
        {timeout: 2000}
      )
    ).toBeInTheDocument();

    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();

    userEvent.click(
      await screen.findByRole('button', {
        name: 'Unfold Filter-Map Sub Process',
      })
    );

    expect(
      await screen.findByText(
        'Start Filter-Map',
        {},
        {
          timeout: 2000,
        }
      )
    ).toBeInTheDocument();
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

    userEvent.click(
      screen.getByRole('button', {
        name: 'Fold Filter-Map Sub Process',
      })
    );

    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();
  });

  it('should poll for instances on root level', async () => {
    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    render(
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={mockFlowNodeInstance}
        isLastChild={false}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    const withinMultiInstanceFlowNode = within(
      screen.getByTestId(
        `tree-node-${flowNodeInstances.level1Poll[processInstanceId].children[1].id}`
      )
    );

    expect(
      await withinMultiInstanceFlowNode.findByTestId('INCIDENT-icon')
    ).toBeInTheDocument();
    expect(
      withinMultiInstanceFlowNode.queryByTestId('COMPLETED-icon')
    ).not.toBeInTheDocument();

    // poll request
    mockServer.use(
      rest.get(`/api/process-instances/:processInstanceId`, (_, res, ctx) =>
        res.once(ctx.json({...CURRENT_INSTANCE}))
      ),
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(flowNodeInstances.level1Poll))
      )
    );
    jest.runOnlyPendingTimers();

    expect(
      await withinMultiInstanceFlowNode.findByTestId('COMPLETED-icon')
    ).toBeInTheDocument();
    expect(
      withinMultiInstanceFlowNode.queryByTestId('INCIDENT-icon')
    ).not.toBeInTheDocument();
  });
});
