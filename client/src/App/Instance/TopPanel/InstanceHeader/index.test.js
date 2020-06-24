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
import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';
import {formatDate} from 'modules/utils/date';
import {getWorkflowName} from 'modules/utils/instance';
import InstanceHeader from './index';
import {currentInstance} from 'modules/stores/currentInstance';
import {variables} from 'modules/stores/variables';
import PropTypes from 'prop-types';

jest.mock('modules/api/instances', () => ({
  fetchWorkflowInstance: jest.fn().mockImplementation((instance_id) => {
    const {createInstance} = require('modules/testUtils');
    const mockInstance = createInstance();
    if (instance_id === 'instance_with_active_operations') {
      return mockInstance;
    } else if (instance_id === 'instance_without_active_operations')
      return {...mockInstance, hasActiveOperation: false, operations: []};
  }),
  applyOperation: jest.fn(),
  fetchVariables: jest
    .fn()
    .mockImplementation(() => [
      {name: 'key', value: 'value', hasActiveOperation: false},
    ]),
}));

let dataManager = createMockDataManager();

const Wrapper = ({children}) => {
  return <DataManagerProvider>{children}</DataManagerProvider>;
};
Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};

describe('InstanceHeader', () => {
  beforeEach(() => {
    currentInstance.reset();
    jest.clearAllMocks();
  });
  afterAll(() => {
    variables.reset();
  });

  it('should show skeleton before instance data is available', async () => {
    render(<InstanceHeader.WrappedComponent dataManager={dataManager} />, {
      wrapper: Wrapper,
    });
    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    await currentInstance.fetchCurrentInstance(
      'instance_with_active_operations'
    );

    expect(
      screen.queryByTestId('instance-header-skeleton')
    ).not.toBeInTheDocument();
  });

  it('should render instance data', async () => {
    render(<InstanceHeader.WrappedComponent dataManager={dataManager} />, {
      wrapper: Wrapper,
    });

    await currentInstance.fetchCurrentInstance(
      'instance_with_active_operations'
    );
    const {instance: mockInstance} = currentInstance.state;
    const workflowName = getWorkflowName(mockInstance);
    const instanceState = mockInstance.state;
    const formattedStartDate = formatDate(mockInstance.startDate);
    const formattedEndDate = formatDate(mockInstance.endDate);

    expect(screen.getByText(workflowName)).toBeInTheDocument();
    expect(screen.getByText(mockInstance.id)).toBeInTheDocument();
    expect(
      screen.getByText(`Version ${mockInstance.workflowVersion}`)
    ).toBeInTheDocument();
    expect(screen.getByText(formattedStartDate)).toBeInTheDocument();
    expect(screen.getByText(formattedEndDate)).toBeInTheDocument();
    expect(screen.getByTestId(`${instanceState}-icon`)).toBeInTheDocument();
  });

  it('should show spinner based on instance having active operations', async () => {
    render(<InstanceHeader.WrappedComponent dataManager={dataManager} />, {
      wrapper: Wrapper,
    });

    await currentInstance.fetchCurrentInstance(
      'instance_without_active_operations'
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    await currentInstance.fetchCurrentInstance(
      'instance_with_active_operations'
    );

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should show spinner when operation is applied', async () => {
    render(<InstanceHeader.WrappedComponent dataManager={dataManager} />, {
      wrapper: Wrapper,
    });

    await currentInstance.fetchCurrentInstance(
      'instance_without_active_operations'
    );
    const {instance} = currentInstance.state;

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    const subscriptions = dataManager.subscriptions();

    dataManager.publish({
      subscription: subscriptions[`OPERATION_APPLIED_INCIDENT_${instance.id}`],
      state: 'LOADING',
    });

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should show spinner when variables is updated', async () => {
    render(<InstanceHeader.WrappedComponent dataManager={dataManager} />, {
      wrapper: Wrapper,
    });

    await currentInstance.fetchCurrentInstance(
      'instance_without_active_operations'
    );

    // spinner is not in the document initially
    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    variables.addVariable('id', 'key', 'value');
    // spinner is displayed after save variable operation triggered
    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

    waitForElementToBeRemoved(screen.queryByTestId('operation-spinner'));
  });
});
