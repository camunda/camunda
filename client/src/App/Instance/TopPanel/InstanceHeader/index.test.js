/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
  fireEvent,
} from '@testing-library/react';
import {formatDate} from 'modules/utils/date';
import {getWorkflowName} from 'modules/utils/instance';
import {InstanceHeader} from './index';
import {currentInstance} from 'modules/stores/currentInstance';
import {variables} from 'modules/stores/variables';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {operationsStore} from 'modules/stores/operations';
import {
  mockInstanceWithActiveOperation,
  mockInstanceWithoutOperations,
  mockOperationCreated,
} from './index.setup';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

describe('InstanceHeader', () => {
  afterEach(() => {
    operationsStore.reset();
    variables.reset();
    currentInstance.reset();
  });

  it('should show skeleton before instance data is available', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      )
    );

    render(<InstanceHeader />, {wrapper: ThemeProvider});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    currentInstance.init(mockInstanceWithActiveOperation.id);

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
  });

  it('should render instance data', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      )
    );
    render(<InstanceHeader />, {wrapper: ThemeProvider});

    currentInstance.init(mockInstanceWithActiveOperation.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
    const {instance} = currentInstance.state;
    const workflowName = getWorkflowName(instance);
    const instanceState = instance.state;
    const formattedStartDate = formatDate(instance.startDate);
    const formattedEndDate = formatDate(instance.endDate);

    expect(screen.getByText(workflowName)).toBeInTheDocument();
    expect(screen.getByText(instance.id)).toBeInTheDocument();
    expect(
      screen.getByText(`Version ${instance.workflowVersion}`)
    ).toBeInTheDocument();
    expect(screen.getByText(formattedStartDate)).toBeInTheDocument();
    expect(screen.getByText(formattedEndDate)).toBeInTheDocument();
    expect(screen.getByTestId(`${instanceState}-icon`)).toBeInTheDocument();
  });

  it('should show spinner based on instance having active operations', async () => {
    render(<InstanceHeader />, {wrapper: ThemeProvider});

    mockServer.use(
      rest.get('/api/workflow-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithoutOperations))
      ),
      rest.get('/api/workflow-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      )
    );

    jest.useFakeTimers();
    currentInstance.init(mockInstanceWithoutOperations.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    jest.advanceTimersByTime(5000);

    expect(await screen.findByTestId('operation-spinner')).toBeInTheDocument();
    jest.useRealTimers();
  });

  it('should show spinner when operation is applied', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithoutOperations))
      ),
      rest.post(
        '/api/workflow-instances/:instanceId/operation',
        (_, res, ctx) => res.once(ctx.json(mockOperationCreated))
      )
    );

    render(<InstanceHeader />, {wrapper: ThemeProvider});

    currentInstance.init(mockInstanceWithoutOperations.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    fireEvent.click(
      screen.getByRole('button', {name: new RegExp('Cancel Instance')})
    );

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should show spinner when variables is updated', async () => {
    const mockVariable = {
      name: 'key',
      value: 'value',
      hasActiveOperation: false,
    };

    mockServer.use(
      rest.get('/api/workflow-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithoutOperations))
      ),
      rest.get(
        '/api/workflow-instances/:instanceId/variables?scopeId=:scopeId',
        (_, res, ctx) => res.once(ctx.json([mockVariable]))
      ),
      rest.post(
        '/api/workflow-instances/:instanceId/operation',
        (_, res, ctx) => res.once(ctx.json(null))
      )
    );

    render(<InstanceHeader />, {wrapper: ThemeProvider});
    currentInstance.init(mockInstanceWithActiveOperation.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    variables.addVariable(
      mockInstanceWithoutOperations.id,
      mockVariable.name,
      mockVariable.value
    );

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

    variables.fetchVariables(mockInstanceWithActiveOperation.id);

    await waitForElementToBeRemoved(screen.queryByTestId('operation-spinner'));
  });
});
