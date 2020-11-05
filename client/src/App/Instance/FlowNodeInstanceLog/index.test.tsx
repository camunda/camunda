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
} from '@testing-library/react';

import {FlowNodeInstanceLog} from './index';
import {
  mockSuccessResponseForActivityTree,
  mockFailedResponseForActivityTree,
  mockSuccessResponseForDiagram,
  mockFailedResponseForDiagram,
} from './index.setup';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {fetchActivityInstancesTree} from 'modules/api/activityInstances';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';

jest.mock('modules/utils/bpmn');
jest.mock('modules/api/diagram', () => ({
  fetchWorkflowXML: jest.fn().mockImplementation(() => ''),
}));

jest.mock('modules/api/diagram');
jest.mock('modules/api/activityInstances');

jest.mock('modules/api/instances', () => ({
  fetchWorkflowInstance: jest.fn().mockImplementation(() => {
    return {id: '1', state: 'ACTIVE', workflowName: 'workflowName'};
  }),
}));

describe('FlowNodeInstanceLog', () => {
  beforeAll(() => {
    currentInstanceStore.init(1);
  });
  afterAll(() => {
    currentInstanceStore.reset();
  });
  afterEach(() => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockReset' does not exist on type '(work... Remove this comment to see the full error message
    fetchActivityInstancesTree.mockReset();
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockReset' does not exist on type '(work... Remove this comment to see the full error message
    fetchWorkflowXML.mockReset();
    flowNodeInstanceStore.reset();
    singleInstanceDiagramStore.reset();
  });

  it('should render skeleton when instance tree is not loaded', async () => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValueOnce' does not exist on... Remove this comment to see the full error message
    fetchActivityInstancesTree.mockResolvedValueOnce(
      mockSuccessResponseForActivityTree
    );
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValueOnce' does not exist on... Remove this comment to see the full error message
    fetchWorkflowXML.mockResolvedValueOnce(mockSuccessResponseForDiagram);

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    await singleInstanceDiagramStore.fetchWorkflowXml(1);
    flowNodeInstanceStore.fetchInstanceExecutionHistory(1);

    expect(screen.getByTestId('flownodeInstance-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('flownodeInstance-skeleton')
    );
  });

  it('should render skeleton when instance diagram is not loaded', async () => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValueOnce' does not exist on... Remove this comment to see the full error message
    fetchActivityInstancesTree.mockResolvedValueOnce(
      mockSuccessResponseForActivityTree
    );
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValueOnce' does not exist on... Remove this comment to see the full error message
    fetchWorkflowXML.mockResolvedValueOnce(mockSuccessResponseForDiagram);

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    await flowNodeInstanceStore.fetchInstanceExecutionHistory(1);
    singleInstanceDiagramStore.fetchWorkflowXml(1);

    expect(screen.getByTestId('flownodeInstance-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('flownodeInstance-skeleton')
    );
  });

  it('should display error when instance tree data could not be fetched', async () => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValueOnce' does not exist on... Remove this comment to see the full error message
    fetchActivityInstancesTree.mockResolvedValueOnce(
      mockFailedResponseForActivityTree
    );
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValueOnce' does not exist on... Remove this comment to see the full error message
    fetchWorkflowXML.mockResolvedValueOnce(mockSuccessResponseForDiagram);

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    await singleInstanceDiagramStore.fetchWorkflowXml(1);
    await flowNodeInstanceStore.fetchInstanceExecutionHistory(1);
    expect(
      screen.getByText('Activity Instances could not be fetched')
    ).toBeInTheDocument();
  });

  it('should display error when instance diagram could not be fetched', async () => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValueOnce' does not exist on... Remove this comment to see the full error message
    fetchActivityInstancesTree.mockResolvedValueOnce(
      mockSuccessResponseForActivityTree
    );
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValueOnce' does not exist on... Remove this comment to see the full error message
    fetchWorkflowXML.mockResolvedValueOnce(mockFailedResponseForDiagram);

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    await singleInstanceDiagramStore.fetchWorkflowXml(1);
    await flowNodeInstanceStore.fetchInstanceExecutionHistory(1);
    expect(
      screen.getByText('Activity Instances could not be fetched')
    ).toBeInTheDocument();
  });

  it('should render flow node instances tree', async () => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValueOnce' does not exist on... Remove this comment to see the full error message
    fetchActivityInstancesTree.mockResolvedValueOnce(
      mockSuccessResponseForActivityTree
    );
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockResolvedValueOnce' does not exist on... Remove this comment to see the full error message
    fetchWorkflowXML.mockResolvedValueOnce(mockSuccessResponseForDiagram);

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    await singleInstanceDiagramStore.fetchWorkflowXml(1);
    await flowNodeInstanceStore.fetchInstanceExecutionHistory(1);
    expect(screen.getAllByText('workflowName').length).toBeGreaterThan(0);
  });
});
