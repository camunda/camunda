/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import {LOADING_STATE} from 'modules/constants';

import {DataManager} from 'modules/DataManager/core';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';

import {
  mockProps,
  mockPropsNoWorkflowSelected,
  mockPropsNoVersionSelected,
  mockPropsNoDefinitions
} from './DiagramPanel.setup';

import SplitPane from 'modules/components/SplitPane';

import Diagram from 'modules/components/Diagram';
import DiagramPanel from './DiagramPanel';
import EmptyMessage from '../EmptyMessage';
const DiagramPanelWrapped = DiagramPanel.WrappedComponent;

jest.mock('modules/utils/bpmn');

jest.mock(
  'modules/components/Diagram',
  () =>
    function Diagram(props) {
      return <div />;
    }
);

jest.mock('modules/DataManager/core');

DataManager.mockImplementation(() => {
  return {
    subscribe: jest.fn(),
    getWorkflowXML: jest.fn(),
    getWorkflowInstances: jest.fn(),
    getWorkflowInstancesStatistics: jest.fn()
  };
});

describe('DiagramPanel', () => {
  let dataManager;
  let node;
  let diagramPanel;
  beforeEach(() => {
    jest.clearAllMocks();
    dataManager = new DataManager();
  });
  describe('DiagramPanel', () => {
    it('should render a spit pane', () => {
      const node = mount(
        <ThemeProvider>
          <CollapsablePanelProvider>
            <DiagramPanelWrapped {...mockProps} {...{dataManager}} />
          </CollapsablePanelProvider>
        </ThemeProvider>
      );
      expect(node.find(SplitPane.Pane)).toExist();
    });

    describe('loading indicator', () => {
      beforeEach(() => {
        node = mount(
          <ThemeProvider>
            <CollapsablePanelProvider>
              <DiagramPanelWrapped {...mockProps} {...{dataManager}} />
            </CollapsablePanelProvider>
          </ThemeProvider>
        );
        diagramPanel = node.find(DiagramPanelWrapped).instance();
      });

      it('should show the indicator, when diagram is loading', () => {
        // when
        diagramPanel.subscriptions['LOAD_STATE_DEFINITIONS']({
          state: LOADING_STATE.LOADING
        });

        node.update();

        // then
        expect(diagramPanel.state.isLoading).toBe(LOADING_STATE.LOADING);
        expect(node.find('[data-test="spinner"]')).toExist();

        // when
        diagramPanel.subscriptions['LOAD_STATE_DEFINITIONS']({
          state: LOADING_STATE.LOADED
        });

        node.update();
        // then - the spinner should not disappear
        expect(diagramPanel.state.isLoading).toBe(LOADING_STATE.LOADING);
        expect(node.find('[data-test="spinner"]')).toExist();
      });

      it('should stop show the indicator, when statistics are loaded', () => {
        // given
        diagramPanel.subscriptions['LOAD_STATE_DEFINITIONS']({
          state: LOADING_STATE.LOADING
        });
        node.update();

        expect(node.find('[data-test="spinner"]')).toExist();
        expect(diagramPanel.state.isLoading).toBe(LOADING_STATE.LOADING);
        // when
        diagramPanel.subscriptions['LOAD_STATE_STATISTICS']({
          state: LOADING_STATE.LOADED
        });

        node.update();

        // then
        expect(diagramPanel.state.isLoading).toBe(LOADING_STATE.LOADED);
        expect(node.find('[data-test="spinner"]')).not.toExist();
      });
    });

    describe('Diagram', () => {
      it('should only display a diagram when a definition is present', () => {
        // given
        node = mount(
          <ThemeProvider>
            <CollapsablePanelProvider>
              <DiagramPanelWrapped
                {...mockPropsNoDefinitions}
                {...{dataManager}}
              />
            </CollapsablePanelProvider>
          </ThemeProvider>
        );

        expect(node.find(Diagram)).not.toExist();
      });
    });

    describe('Messages', () => {
      it('should render "no workflow" message, when no workflow is present', () => {
        // given
        node = mount(
          <ThemeProvider>
            <CollapsablePanelProvider>
              <DiagramPanelWrapped
                {...mockPropsNoWorkflowSelected}
                {...{dataManager}}
              />
            </CollapsablePanelProvider>
          </ThemeProvider>
        );
        diagramPanel = node.find(DiagramPanelWrapped).instance();

        expect(node.find(Diagram)).not.toExist();
        expect(node.find(EmptyMessage)).toExist();
      });

      it('should render "no Version" message, no specific workflow version is selected ', () => {
        // given
        node = mount(
          <ThemeProvider>
            <CollapsablePanelProvider>
              <DiagramPanelWrapped
                {...mockPropsNoVersionSelected}
                {...{dataManager}}
              />
            </CollapsablePanelProvider>
          </ThemeProvider>
        );

        expect(node.find(Diagram)).not.toExist();
        expect(node.find(EmptyMessage)).toExist();
      });
    });
  });
});
