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

import IncidentAction from './IncidentAction';
import ActionStatus from 'modules/components/ActionStatus';
import ActionItems from './ActionItems';
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

const mountIncidentAction = props => {
  createMockDataManager();
  return mount(
    <ThemeProvider>
      <DataManagerProvider>
        <IncidentAction {...props} />
      </DataManagerProvider>
    </ThemeProvider>
  );
};

describe('IncidentAction', () => {
  it('should render a spinner if showSpinner prop is true', () => {
    const node = mountIncidentAction({...mockProps, showSpinner: true});
    expect(node.find(ActionStatus.Spinner)).toExist();
  });

  it('should not render a spinner if showSpinner prop is false', () => {
    const node = mountIncidentAction(mockProps);

    expect(node.find(ActionStatus.Spinner)).not.toExist();
  });

  it('should render spinner when instance operation is published', () => {
    const node = mountIncidentAction(mockProps);

    // given
    const {dataManager} = node
      .find(IncidentAction.WrappedComponent)
      .instance().props;
    const {subscriptions} = node
      .find(IncidentAction.WrappedComponent)
      .instance();

    expect(node.find(ActionStatus.Spinner)).not.toExist();

    dataManager.publish({
      subscription: subscriptions['OPERATION_APPLIED_INSTANCE_instance_1'],
      state: LOADING_STATE.LOADING
    });

    node.update();

    expect(node.find(ActionStatus.Spinner)).toExist();
  });

  describe('Action Buttons', () => {
    it('should render a retry button', () => {
      const node = mountIncidentAction(mockProps);

      const ItemNode = node.find(ActionItems.Item);
      expect(ItemNode).toExist();
      expect(ItemNode.props().type).toEqual(OPERATION_TYPE.RESOLVE_INCIDENT);
      expect(ItemNode.props().title).toEqual('Retry Incident');
    });

    it('should render show a spinner after retry button is clicked', async () => {
      const node = mountIncidentAction(mockProps);

      const ItemNode = node.find(ActionItems.Item);

      ItemNode.find('button').simulate('click');
      // await for operation response
      await flushPromises();
      node.update();

      expect(node.find(ActionStatus.Spinner)).toExist();
    });

    it('should render start an operation when retry button is clicked', async () => {
      const node = mountIncidentAction(mockProps);

      const {dataManager} = node
        .find(IncidentAction.WrappedComponent)
        .instance().props;

      const ItemNode = node.find(ActionItems.Item);

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
