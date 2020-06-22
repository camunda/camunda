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

import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';

import {SUBSCRIPTION_TOPIC, LOADING_STATE} from 'modules/constants';
import {mockProps, mockedExpandedPaneId} from './index.setup';

import {
  mockedModules,
  mockedImportDefinitions,
} from '__mocks__/bpmn-js/lib/NavigatedViewer';

import IncidentsWrapper from '../IncidentsWrapper';

import Diagram from 'modules/components/Diagram';
import SpinnerSkeleton from 'modules/components/SpinnerSkeleton';
import SplitPane from 'modules/components/SplitPane';
import {ThemeProvider} from 'modules/theme';

import TopPanel from './index';
import {currentInstance} from 'modules/stores/currentInstance';
import {flushPromises} from 'modules/testUtils';

jest.mock('modules/utils/bpmn');

jest.mock('../IncidentsWrapper', () => {
  /* eslint react/prop-types: 0  */
  return function IncidentsWrapper(props) {
    return <div />;
  };
});

jest.mock('modules/api/instances', () => ({
  fetchWorkflowInstance: jest
    .fn()
    .mockImplementation(() => ({state: 'INCIDENT'})),
}));

jest.mock('./InstanceHeader', () => {
  /* eslint react/prop-types: 0  */
  return function InstanceHeader(props) {
    return <div />;
  };
});

createMockDataManager();
const mountTopPanel = (props) => {
  return mount(
    <ThemeProvider>
      <DataManagerProvider>
        <SplitPane expandedPaneId={mockedExpandedPaneId}>
          <TopPanel {...props} />
          <SplitPane.Pane />
        </SplitPane>
      </DataManagerProvider>
    </ThemeProvider>
  );
};

describe('DiagramPanel', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should render spinner by default', () => {
    // when
    const node = mountTopPanel({...mockProps});

    // then
    expect(node.find(SpinnerSkeleton)).toHaveLength(1);
    expect(node.find(Diagram)).not.toHaveLength(1);
    expect(node.find(IncidentsWrapper)).not.toHaveLength(1);
  });

  it('should render diagram', async () => {
    // given
    await currentInstance.fetchCurrentInstance(1);
    const node = mountTopPanel({...mockProps});
    const {props, subscriptions} = node
      .find(TopPanel.WrappedComponent)
      .instance();

    const {dataManager} = props;
    // when
    dataManager.publish({
      subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS],
      state: LOADING_STATE.LOADED,
    });

    await flushPromises();
    node.update();
    // then
    expect(node.find(SpinnerSkeleton)).not.toHaveLength(1);
    expect(node.find(Diagram)).toHaveLength(1);

    expect(mockedImportDefinitions).toHaveBeenCalled();

    // it should apply zoom to the diagram
    expect(mockedModules.canvas.zoom).toHaveBeenCalled();
    expect(mockedModules.canvas.resized).toHaveBeenCalled();
  });

  it('should render hidden diagram', () => {
    // given
    localStorage.setItem(
      'panelStates',
      JSON.stringify({[mockedExpandedPaneId]: 'COLLAPSED'})
    );

    const node = mountTopPanel({...mockProps});
    const {props, subscriptions} = node
      .find(TopPanel.WrappedComponent)
      .instance();

    const {dataManager} = props;

    // when
    dataManager.publish({
      subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS],
      state: LOADING_STATE.LOADED,
    });

    node.update();

    // then
    expect(node.find(SpinnerSkeleton)).not.toHaveLength(1);
    expect(node.find(Diagram)).toHaveLength(1);

    expect(mockedImportDefinitions).toHaveBeenCalled();

    // it should not interact with NavigatedViewer, when the panel is collapsed
    expect(mockedModules.canvas.zoom).not.toHaveBeenCalled();
    expect(mockedModules.canvas.resized).not.toHaveBeenCalled();
    expect(mockedModules.zoomScroll.stepZoom).not.toHaveBeenCalled();

    localStorage.clear();
  });

  it('should render incidentsTable', async () => {
    // given

    await currentInstance.fetchCurrentInstance(1);

    const node = mountTopPanel({...mockProps});

    const {props, subscriptions} = node
      .find(TopPanel.WrappedComponent)
      .instance();

    const {dataManager} = props;

    // when
    dataManager.publish({
      subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_STATE_DEFINITIONS],
      state: LOADING_STATE.LOADED,
    });

    node.update();

    // then
    expect(node.find(IncidentsWrapper)).toHaveLength(1);
  });
});
