/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {
  mockResolvedAsyncFn,
  createIncident,
  flushPromises
} from 'modules/testUtils';

import {OPERATION_TYPE} from 'modules/constants';

import IncidentAction from './IncidentAction';
import ActionStatus from 'modules/components/ActionStatus';
import ActionItems from './ActionItems';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import * as api from 'modules/api/instances/instances';

// mocking api
api.applyOperation = mockResolvedAsyncFn({count: 1, reason: null});
const mockProps = {
  incident: createIncident(),
  onButtonClick: jest.fn(),
  instanceId: 'instance_1',
  showSpinner: false
};

describe('IncidentAction', () => {
  it('should render a spinner if showSpinner prop is true', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentAction {...{...mockProps, showSpinner: true}} />
      </ThemeProvider>
    );

    expect(node.find(ActionStatus.Spinner)).toExist();
  });
  it('should not render a spinner if showSpinner prop is false', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentAction {...mockProps} />
      </ThemeProvider>
    );

    expect(node.find(ActionStatus.Spinner)).not.toExist();
  });

  describe('Action Buttons', () => {
    it('should render a retry button', () => {
      const node = mount(
        <ThemeProvider>
          <IncidentAction {...mockProps} />
        </ThemeProvider>
      );

      const ItemNode = node.find(ActionItems.Item);
      expect(ItemNode).toExist();
      expect(ItemNode.props().type).toEqual(OPERATION_TYPE.RESOLVE_INCIDENT);
      expect(ItemNode.props().title).toEqual('Retry Incident');
    });

    it('should render show a spinner after retry button is clicked', async () => {
      const node = mount(
        <ThemeProvider>
          <IncidentAction {...mockProps} />
        </ThemeProvider>
      );

      const ItemNode = node.find(ActionItems.Item);

      ItemNode.find('button').simulate('click');
      // await for operation response
      await flushPromises();
      node.update();

      expect(node.find(ActionStatus.Spinner)).toExist();
    });

    it('should render start an operation when retry button is clicked', async () => {
      const node = mount(
        <ThemeProvider>
          <IncidentAction {...mockProps} />
        </ThemeProvider>
      );

      const ItemNode = node.find(ActionItems.Item);

      ItemNode.find('button').simulate('click');
      // await for operation response
      await flushPromises();
      node.update();

      expect(api.applyOperation).toHaveBeenCalledWith(mockProps.instanceId, {
        operationType: OPERATION_TYPE.RESOLVE_INCIDENT,
        incidentId: mockProps.incident.id
      });
      expect(mockProps.onButtonClick).toHaveBeenCalled();
    });
  });
});
