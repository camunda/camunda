/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {
  mockProps,
  multipleVariableScopes,
  noVariableScopes
} from './VariablePanel.setup';

import {DataManagerProvider} from 'modules/DataManager';
import VariablePanel from './VariablePanel';
import {LOADING_STATE} from 'modules/constants';
import {SUBSCRIPTION_TOPIC} from 'modules/constants';

import {
  FAILED_PLACEHOLDER,
  MULTI_SCOPE_PLACEHOLDER,
  EMPTY_PLACEHOLDER
} from './constants';

import EmptyPanel from 'modules/components/EmptyPanel';
import Skeleton from './Skeleton';
import SpinnerSkeleton from 'modules/components/Skeletons';

// DataManager mock
import * as dataManagerHelper from 'modules/testHelpers/dataManager';
import {DataManager} from 'modules/DataManager/core';
// import {EmptyPanel} from './styled';

jest.mock('modules/DataManager/core');
DataManager.mockImplementation(dataManagerHelper.mockDataManager);

jest.mock('modules/utils/bpmn');

const mountRenderComponent = (customProps = {}) => {
  return mount(
    <ThemeProvider>
      <DataManagerProvider dataManager={new DataManager()}>
        <VariablePanel {...customProps} />
      </DataManagerProvider>
    </ThemeProvider>
  );
};

describe('VariablePanel', () => {
  let node, root;
  beforeEach(() => {
    root = mountRenderComponent(mockProps);
    node = root.find('VariablePanel');
  });

  it('should subscribe and unsubscribe on un/mount', () => {
    //given
    const {
      props: {dataManager},
      subscriptions
    } = node.instance();

    //then
    expect(dataManager.subscribe).toHaveBeenCalledWith(subscriptions);

    //when
    root.unmount();
    //then
    expect(dataManager.unsubscribe).toHaveBeenCalledWith(subscriptions);
  });

  it('should show the Message for selected multi scope cases', () => {
    //given
    root = mountRenderComponent(multipleVariableScopes);

    const {
      props: {dataManager},
      subscriptions
    } = root.find('VariablePanel').instance();

    dataManager.publish({
      subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_VARIABLES],
      state: LOADING_STATE.LOADED
    });

    root.update();

    const node = root.find('VariablePanel');

    expect(node.find('EmptyPanel')).toExist();
    expect(node.text()).toContain(MULTI_SCOPE_PLACEHOLDER);
  });

  it('should show the Message for failed data fetches', () => {
    //given
    root = mountRenderComponent(multipleVariableScopes);

    const {
      props: {dataManager},
      subscriptions
    } = root.find('VariablePanel').instance();

    dataManager.publish({
      subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_VARIABLES],
      state: LOADING_STATE.LOAD_FAILED
    });

    root.update();

    const node = root.find('VariablePanel');

    expect(node.find('EmptyPanel')).toExist();
    expect(node.text()).toContain(FAILED_PLACEHOLDER);
  });

  describe('Placeholder', () => {
    it('should show skeleton when initially loaded', () => {
      //given
      root = mountRenderComponent(mockProps);

      const node = root.find('VariablePanel');

      expect(
        node
          .find('Variables')
          .props()
          .Placeholder()
      ).toMatchSnapshot();
    });

    it('should show the Message when scope doesnt have variable ', () => {
      //given
      root = mountRenderComponent(noVariableScopes);

      const {
        props: {dataManager},
        subscriptions
      } = root.find('VariablePanel').instance();

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_VARIABLES],
        state: LOADING_STATE.LOADED
      });

      root.update();

      const node = root.find('VariablePanel');

      expect(
        node
          .find('Variables')
          .props()
          .Placeholder()
      ).toMatchSnapshot();
    });
  });

  describe('Spinner', () => {
    it('should render spinner when data is loading', () => {
      //given
      root = mountRenderComponent(noVariableScopes);

      const {
        props: {dataManager},
        subscriptions
      } = root.find('VariablePanel').instance();

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_VARIABLES],
        state: LOADING_STATE.LOADED
      });

      dataManager.publish({
        subscription: subscriptions[SUBSCRIPTION_TOPIC.LOAD_VARIABLES],
        state: LOADING_STATE.LOADING
      });

      root.update();

      const node = root.find('VariablePanel');

      expect(node.find(SpinnerSkeleton)).toExist();
      expect(
        node
          .find('Variables')
          .props()
          .Overlay()
      ).toMatchSnapshot();
    });
  });
});
