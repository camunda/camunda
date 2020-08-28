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
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {currentInstance} from 'modules/stores/currentInstance';
import {fetchActivityInstancesTree} from 'modules/api/activityInstances';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {singleInstanceDiagram} from 'modules/stores/singleInstanceDiagram';

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
    currentInstance.init(1);
  });
  afterAll(() => {
    currentInstance.reset();
  });
  afterEach(() => {
    fetchActivityInstancesTree.mockReset();
    fetchWorkflowXML.mockReset();
    flowNodeInstance.reset();
    singleInstanceDiagram.reset();
  });

  it('should render skeleton when instance tree is not loaded', async () => {
    fetchActivityInstancesTree.mockResolvedValueOnce(
      mockSuccessResponseForActivityTree
    );
    fetchWorkflowXML.mockResolvedValueOnce(mockSuccessResponseForDiagram);

    render(<FlowNodeInstanceLog />);

    await singleInstanceDiagram.fetchWorkflowXml(1);
    flowNodeInstance.fetchInstanceExecutionHistory(1);

    expect(screen.getByTestId('flownodeInstance-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('flownodeInstance-skeleton')
    );
  });

  it('should render skeleton when instance diagram is not loaded', async () => {
    fetchActivityInstancesTree.mockResolvedValueOnce(
      mockSuccessResponseForActivityTree
    );
    fetchWorkflowXML.mockResolvedValueOnce(mockSuccessResponseForDiagram);

    render(<FlowNodeInstanceLog />);

    await flowNodeInstance.fetchInstanceExecutionHistory(1);
    singleInstanceDiagram.fetchWorkflowXml(1);

    expect(screen.getByTestId('flownodeInstance-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('flownodeInstance-skeleton')
    );
  });

  it('should display error when instance tree data could not be fetched', async () => {
    fetchActivityInstancesTree.mockResolvedValueOnce(
      mockFailedResponseForActivityTree
    );
    fetchWorkflowXML.mockResolvedValueOnce(mockSuccessResponseForDiagram);

    render(<FlowNodeInstanceLog />);

    await singleInstanceDiagram.fetchWorkflowXml(1);
    await flowNodeInstance.fetchInstanceExecutionHistory(1);
    expect(
      screen.getByText('Activity Instances could not be fetched')
    ).toBeInTheDocument();
  });

  it('should display error when instance diagram could not be fetched', async () => {
    fetchActivityInstancesTree.mockResolvedValueOnce(
      mockSuccessResponseForActivityTree
    );
    fetchWorkflowXML.mockResolvedValueOnce(mockFailedResponseForDiagram);

    render(<FlowNodeInstanceLog />);

    await singleInstanceDiagram.fetchWorkflowXml(1);
    await flowNodeInstance.fetchInstanceExecutionHistory(1);
    expect(
      screen.getByText('Activity Instances could not be fetched')
    ).toBeInTheDocument();
  });

  it('should render flow node instances tree', async () => {
    fetchActivityInstancesTree.mockResolvedValueOnce(
      mockSuccessResponseForActivityTree
    );
    fetchWorkflowXML.mockResolvedValueOnce(mockSuccessResponseForDiagram);

    render(<FlowNodeInstanceLog />);

    await singleInstanceDiagram.fetchWorkflowXml(1);
    await flowNodeInstance.fetchInstanceExecutionHistory(1);
    expect(screen.getAllByText('workflowName').length).toBeGreaterThan(0);
  });
});
