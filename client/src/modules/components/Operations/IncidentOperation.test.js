/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';

import {createIncident, flushPromises} from 'modules/testUtils';

import {OPERATION_TYPE, LOADING_STATE} from 'modules/constants';

import IncidentOperation from './IncidentOperation';
import OperationStatus from 'modules/components/OperationStatus';
import OperationItems from './OperationItems';
import {ThemeProvider} from 'modules/contexts/ThemeContext';

jest.mock('modules/DataManager/core');
jest.mock('modules/utils/bpmn');

// mocking api

const mockProps = {
  incident: createIncident(),
  onButtonClick: jest.fn(),
  instanceId: 'instance_1',
  showSpinner: false
};

const mountIncidentOperation = props => {
  createMockDataManager();
  return mount(
    <ThemeProvider>
      <DataManagerProvider>
        <IncidentOperation {...props} />
      </DataManagerProvider>
    </ThemeProvider>
  );
};

describe('IncidentOperation', () => {
  it('should render a spinner if showSpinner prop is true', () => {
    const node = mountIncidentOperation({...mockProps, showSpinner: true});
    expect(node.find(OperationStatus.Spinner)).toExist();
  });

  it('should not render a spinner if showSpinner prop is false', () => {
    const node = mountIncidentOperation(mockProps);

    expect(node.find(OperationStatus.Spinner)).not.toExist();
  });

  it('should render spinner when instance operation is published', () => {
    const node = mountIncidentOperation(mockProps);

    // given
    const {dataManager} = node
      .find(IncidentOperation.WrappedComponent)
      .instance().props;
    const {subscriptions} = node
      .find(IncidentOperation.WrappedComponent)
      .instance();

    expect(node.find(OperationStatus.Spinner)).not.toExist();

    dataManager.publish({
      subscription: subscriptions['OPERATION_APPLIED_INSTANCE_instance_1'],
      state: LOADING_STATE.LOADING
    });

    node.update();

    expect(node.find(OperationStatus.Spinner)).toExist();
  });

  describe('Operation Buttons', () => {
    it('should render a retry button', () => {
      const node = mountIncidentOperation(mockProps);

      const ItemNode = node.find(OperationItems.Item);
      expect(ItemNode).toExist();
      expect(ItemNode.props().type).toEqual(OPERATION_TYPE.RESOLVE_INCIDENT);
      expect(ItemNode.props().title).toEqual('Retry Incident');
    });

    it('should render show a spinner after retry button is clicked', async () => {
      const node = mountIncidentOperation(mockProps);

      const ItemNode = node.find(OperationItems.Item);

      ItemNode.find('button').simulate('click');
      // await for operation response
      await flushPromises();
      node.update();

      expect(node.find(OperationStatus.Spinner)).toExist();
    });

    it('should render start an operation when retry button is clicked', async () => {
      const node = mountIncidentOperation(mockProps);

      const {dataManager} = node
        .find(IncidentOperation.WrappedComponent)
        .instance().props;

      const ItemNode = node.find(OperationItems.Item);

      ItemNode.find('button').simulate('click');

      expect(dataManager.applyOperation).toHaveBeenCalledWith(
        mockProps.instanceId,
        {
          operationType: OPERATION_TYPE.RESOLVE_INCIDENT,
          incidentId: mockProps.incident.id
        }
      );
    });
  });
});
