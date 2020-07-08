/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';

import {SUBSCRIPTION_TOPIC, LOADING_STATE} from 'modules/constants';
import FlowNodeInstanceLog from './index';

import {
  mockSuccessResponseForActivityTree,
  mockFailedResponseForActivityTree,
  mockProps,
} from './index.setup';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {currentInstance} from 'modules/stores/currentInstance';
import {FlowNodeTimeStampProvider} from 'modules/contexts/FlowNodeTimeStampContext';
import {fetchActivityInstancesTree} from 'modules/api/activityInstances';

let dataManager = createMockDataManager();

jest.mock('modules/api/activityInstances');

jest.mock('modules/api/instances', () => ({
  fetchWorkflowInstance: jest.fn().mockImplementation(() => {
    return {id: '1', state: 'ACTIVE'};
  }),
}));

const Wrapper = ({children}) => {
  return (
    <DataManagerProvider>
      <FlowNodeTimeStampProvider>{children} </FlowNodeTimeStampProvider>
    </DataManagerProvider>
  );
};
Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};
describe('FlowNodeInstanceLog', () => {
  beforeAll(async () => {
    await currentInstance.fetchCurrentInstance(1);
  });
  afterEach(() => {
    fetchActivityInstancesTree.mockReset();
  });

  beforeEach(() => {
    flowNodeInstance.reset();
  });

  it('should render skeleton when instance tree is not loaded', async () => {
    fetchActivityInstancesTree.mockResolvedValueOnce(
      mockSuccessResponseForActivityTree
    );

    render(
      <FlowNodeInstanceLog.WrappedComponent
        {...mockProps}
        dataManager={dataManager}
      />,
      {
        wrapper: Wrapper,
      }
    );

    dataManager.publish({
      subscription: dataManager.subscriptions()[
        SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS
      ],
      state: LOADING_STATE.LOADED,
    });

    flowNodeInstance.fetchInstanceExecutionHistory(1);

    expect(screen.getByTestId('flownodeInstance-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('flownodeInstance-skeleton')
    );
  });

  it('should display error when instance tree data could not be fetched', async () => {
    fetchActivityInstancesTree.mockResolvedValueOnce(
      mockFailedResponseForActivityTree
    );

    render(
      <FlowNodeInstanceLog.WrappedComponent
        {...mockProps}
        dataManager={dataManager}
      />,
      {
        wrapper: Wrapper,
      }
    );

    dataManager.publish({
      subscription: dataManager.subscriptions()[
        SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS
      ],
      state: LOADING_STATE.LOADED,
    });

    await flowNodeInstance.fetchInstanceExecutionHistory(1);
    expect(
      screen.getByText('Activity Instances could not be fetched')
    ).toBeInTheDocument();
  });

  it('should render flow node instances tree', async () => {
    fetchActivityInstancesTree.mockResolvedValueOnce(
      mockSuccessResponseForActivityTree
    );

    render(
      <FlowNodeInstanceLog.WrappedComponent
        {...mockProps}
        dataManager={dataManager}
      />,
      {
        wrapper: Wrapper,
      }
    );

    dataManager.publish({
      subscription: dataManager.subscriptions()[
        SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS
      ],
      state: LOADING_STATE.LOADED,
    });
    await flowNodeInstance.fetchInstanceExecutionHistory(1);
    expect(screen.getAllByText('nodeName').length).toBeGreaterThan(0);
  });
});
