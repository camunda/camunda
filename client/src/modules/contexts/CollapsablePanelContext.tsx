/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

const CollapsablePanelContext = React.createContext({});

const CollapsablePanelConsumer = CollapsablePanelContext.Consumer;

type State = {
  isFiltersCollapsed: boolean;
  isOperationsCollapsed: boolean;
};

class CollapsablePanelProvider extends React.Component<{}, State> {
  state = {isFiltersCollapsed: false, isOperationsCollapsed: true};

  toggle = (target: 'isFiltersCollapsed' | 'isOperationsCollapsed') => {
    const currentPanelState = this.getCurrentPanelState();
    const expandState = {
      [target]: !currentPanelState[target],
    } as Pick<State, keyof State>;

    storeStateLocally(expandState, 'panelStates');

    this.setState(expandState);
  };

  toggleFilters = () => this.toggle('isFiltersCollapsed');

  toggleOperations = () => this.toggle('isOperationsCollapsed');

  expand = (target: 'isFiltersCollapsed' | 'isOperationsCollapsed') => {
    const expandState = {[target]: false} as Pick<State, keyof State>;
    storeStateLocally(expandState, 'panelStates');

    this.setState(expandState);
  };

  expandFilters = () => this.expand('isFiltersCollapsed');

  expandOperations = () => this.expand('isOperationsCollapsed');

  getCurrentPanelState = function () {
    const {isFiltersCollapsed = false, isOperationsCollapsed = true} =
      getStateLocally('panelStates');

    return {
      isFiltersCollapsed,
      isOperationsCollapsed,
    };
  };

  render() {
    const currentPanelState = this.getCurrentPanelState();

    const contextValue = {
      ...currentPanelState,
      toggleFilters: this.toggleFilters,
      toggleOperations: this.toggleOperations,
      expandFilters: this.expandFilters,
      expandOperations: this.expandOperations,
    };

    return (
      <CollapsablePanelContext.Provider value={contextValue}>
        {this.props.children}
      </CollapsablePanelContext.Provider>
    );
  }
}

const withCollapsablePanel = (Component: any) => {
  function WithCollapsablePanel(props: any) {
    return (
      <CollapsablePanelConsumer>
        {(contextValue) => <Component {...props} {...contextValue} />}
      </CollapsablePanelConsumer>
    );
  }

  WithCollapsablePanel.WrappedComponent = Component;

  WithCollapsablePanel.displayName = `WithCollapsablePanel(${
    Component.displayName || Component.name || 'Component'
  })`;

  return WithCollapsablePanel;
};

export {
  CollapsablePanelContext as default,
  CollapsablePanelConsumer,
  CollapsablePanelProvider,
  withCollapsablePanel,
};
