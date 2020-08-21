/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';
import {rest} from 'msw';

import {FlowNodeInstancesTree} from './index';
import {mockServer} from 'modules/mockServer';
import {currentInstance} from 'modules/stores/currentInstance';
import {singleInstanceDiagram} from 'modules/stores/singleInstanceDiagram';

import {DIAGRAM, CURRENT_INSTANCE, mockNode} from './index.setup';

const instanceId = 1;
const workflowId = 1;

describe('<FlowNodeInstancesTree />', () => {
  beforeEach(async () => {
    mockServer.use(
      rest.get(`/api/workflow-instances/${instanceId}`, (_, res, ctx) =>
        res(ctx.json(CURRENT_INSTANCE))
      ),
      rest.get(`/api/workflows/${workflowId}/xml`, (_, res, ctx) =>
        res(ctx.text(DIAGRAM))
      )
    );

    await Promise.all([
      currentInstance.fetchCurrentInstance(instanceId),
      singleInstanceDiagram.fetchWorkflowXml(workflowId),
    ]);
  });

  afterEach(() => {
    currentInstance.reset();
    singleInstanceDiagram.reset();
  });

  it('should load the instance history', async () => {
    render(
      <FlowNodeInstancesTree
        treeDepth={1}
        node={mockNode}
        onTreeRowSelection={() => {}}
      />
    );

    expect(screen.getByText('Multi-Instance Process')).toBeInTheDocument();
    expect(screen.getByText('Peter Fork')).toBeInTheDocument();
    expect(
      screen.getByText('Filter-Map Sub Process (Multi Instance)')
    ).toBeInTheDocument();
  });

  it('should be able to unfold and unfold subprocesses', async () => {
    render(
      <FlowNodeInstancesTree
        treeDepth={1}
        node={mockNode}
        onTreeRowSelection={() => {}}
      />
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
      screen.getByRole('button', {
        name: 'Fold Filter-Map Sub Process (Multi Instance)',
      })
    ).toBeInTheDocument();
    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Unfold Filter-Map Sub Process',
      })
    );

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
    expect(screen.getByText('Start Filter-Map')).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Fold Filter-Map Sub Process',
      })
    );

    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();
  });

  it('should handle clicks on the history', async () => {
    const mockOnTreeRowSelection = jest.fn();

    render(
      <FlowNodeInstancesTree
        treeDepth={1}
        node={mockNode}
        onTreeRowSelection={mockOnTreeRowSelection}
      />
    );

    fireEvent.click(screen.getByText('Peter Fork'));

    expect(mockOnTreeRowSelection).toHaveBeenCalled();
  });
});
