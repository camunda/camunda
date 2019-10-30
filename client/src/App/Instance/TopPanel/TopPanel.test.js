/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import {SUBSCRIPTION_TOPIC, LOADING_STATE} from 'modules/constants';
import {mockProps, instanceWithIncident} from './TopPanel.setup';

import IncidentsWrapper from '../IncidentsWrapper';

import Diagram from 'modules/components/Diagram';
import SpinnerSkeleton from 'modules/components/Skeletons';
import SplitPane from 'modules/components/SplitPane';
import {ThemeProvider} from 'modules/theme';
import {DataManagerProvider} from 'modules/DataManager';

import TopPanel from './TopPanel';

import * as dataManagerHelper from 'modules/testHelpers/dataManager';
import {DataManager} from 'modules/DataManager/core';

jest.mock('modules/DataManager/core');
DataManager.mockImplementation(dataManagerHelper.mockDataManager);

jest.mock('modules/utils/bpmn');

jest.mock('../IncidentsWrapper', () => {
  /* eslint react/prop-types: 0  */
  return function IncidentsWrapper(props) {
    return <div />;
  };
});

jest.mock('./InstanceHeader', () => {
  /* eslint react/prop-types: 0  */
  return function InstanceHeader(props) {
    return <div />;
  };
});

const mountTopPanel = props => {
  return mount(
    <ThemeProvider>
      <DataManagerProvider dataManager={new DataManager()}>
        <SplitPane>
          <TopPanel {...props} />
          <SplitPane.Pane />
        </SplitPane>
      </DataManagerProvider>
    </ThemeProvider>
  );
};

describe('DiagramPanel', () => {
  let node;
  beforeEach(() => {
    node = mountTopPanel(mockProps);
  });

  it('should render spinner by default', () => {
    expect(node.find(SpinnerSkeleton)).toHaveLength(1);
    expect(node.find(Diagram)).not.toHaveLength(1);
    expect(node.find(IncidentsWrapper)).not.toHaveLength(1);
  });

  it('should render diagram', () => {
    const {props, subscriptions} = node
      .find(TopPanel.WrappedComponent)
      .instance();

    const {dataManager} = props;
    // when
    dataManager.publish({
      subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS],
      state: LOADING_STATE.LOADED
    });

    node.update();
    // then
    expect(node.find(SpinnerSkeleton)).not.toHaveLength(1);
    expect(node.find(Diagram)).toHaveLength(1);
  });

  it('should render incidentsTable', () => {
    node = mountTopPanel(instanceWithIncident);
    const {props, subscriptions} = node
      .find(TopPanel.WrappedComponent)
      .instance();

    const {dataManager} = props;
    // when
    dataManager.publish({
      subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS],
      state: LOADING_STATE.LOADED
    });

    node.update();
    expect(node.find(IncidentsWrapper)).toHaveLength(1);
  });
});
