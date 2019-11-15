/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';

import {createInstance} from 'modules/testUtils';

import Pane from 'modules/components/SplitPane/Pane';
import SplitPane from 'modules/components/SplitPane';

import StateIcon from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils/date';
import {
  getWorkflowName,
  getInstancesWithActiveOperations
} from 'modules/utils/instance';
import {ThemeProvider} from 'modules/theme';

import InstanceHeader from './InstanceHeader';
import Actions from 'modules/components/Actions';

import Spinner from 'modules/components/Spinner';

jest.mock('modules/utils/bpmn');

const mockInstance = createInstance();

createMockDataManager();
const mountInstanceHeader = props => {
  return mount(
    <ThemeProvider>
      <DataManagerProvider>
        <SplitPane>
          <Pane>
            <InstanceHeader {...props} />
          </Pane>
          <SplitPane.Pane />
        </SplitPane>
      </DataManagerProvider>
    </ThemeProvider>
  );
};

describe('InstanceHeader', () => {
  let node;
  beforeEach(() => {
    node = mountInstanceHeader({instance: mockInstance});
  });

  it('should show spinner based on instance data', () => {
    node = mountInstanceHeader({
      instance: {...mockInstance, hasActiveOperation: true}
    });

    expect(node.find(Spinner)).toHaveLength(1);
  });

  it('should show spinner when operation is published', () => {
    node = mountInstanceHeader({
      instance: {...mockInstance, hasActiveOperation: false, operations: []}
    });
    const componentNode = node.find(InstanceHeader.WrappedComponent);

    // given
    const {dataManager} = componentNode.instance().props;
    const {subscriptions} = componentNode.instance();

    expect(node.find(Spinner)).not.toHaveLength(1);
    // when

    dataManager.publish({
      subscription: subscriptions['OPERATION_APPLIED_INCIDENT_id_1'],
      state: 'LOADING'
    });

    node.update();

    //then
    expect(node.find(Spinner)).toHaveLength(1);
  });

  it('should render', () => {
    node = mountInstanceHeader({
      instance: mockInstance
    });
    // given
    const workflowName = getWorkflowName(mockInstance);
    const instanceState = mockInstance.state;
    const formattedStartDate = formatDate(mockInstance.startDate);
    const formattedEndDate = formatDate(mockInstance.endDate);

    // then
    expect(node.find(Pane.Header)).toHaveLength(1);

    // Pane.Header
    const PaneHeaderNode = node.find(Pane.Header);

    const TableNode = PaneHeaderNode.find('table');
    expect(TableNode.text()).toContain(workflowName);
    expect(TableNode.text()).toContain(mockInstance.id);
    expect(TableNode.text()).toContain(
      `Version ${mockInstance.workflowVersion}`
    );
    expect(TableNode.text()).toContain(formattedStartDate);
    expect(TableNode.text()).toContain(formattedEndDate);

    const StateIconNode = TableNode.find(StateIcon);
    expect(StateIconNode).toHaveLength(1);
    expect(StateIconNode.prop('state')).toBe(instanceState);

    const ActionsNode = PaneHeaderNode.find(Actions);
    expect(ActionsNode).toExist();
    expect(ActionsNode.props().instance).toEqual(mockInstance);

    expect(ActionsNode.props().forceSpinner).toEqual(
      !!getInstancesWithActiveOperations([mockInstance]).length
    );
  });
});
