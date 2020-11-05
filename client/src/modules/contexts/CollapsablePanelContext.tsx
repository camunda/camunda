/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

// @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
const CollapsablePanelContext = React.createContext();

// Wrapper that passes the theme as a prop
const CollapsablePanelConsumer = CollapsablePanelContext.Consumer;

type State = {
  isFiltersCollapsed: boolean;
  isOperationsCollapsed: boolean;
};

// Top level component to pass down theme in the App
class CollapsablePanelProvider extends React.Component<{}, State> {
  // we start with both panels not collapsed
  state = {isFiltersCollapsed: false, isOperationsCollapsed: true};

  toggle = function (target: any) {
    // @ts-expect-error ts-migrate(2683) FIXME: 'this' implicitly has type 'any' because it does n... Remove this comment to see the full error message
    const currentPanelState = this.getCurrentPanelState();
    const expandState = {[target]: !currentPanelState[target]};

    storeStateLocally(expandState, 'panelStates');
    // components should be rerendered after panel states changed
    // @ts-expect-error ts-migrate(2683) FIXME: 'this' implicitly has type 'any' because it does n... Remove this comment to see the full error message
    this.setState(expandState);
  };

  toggleFilters = () => this.toggle('isFiltersCollapsed');

  toggleOperations = () => this.toggle('isOperationsCollapsed');

  expand = function (target: any) {
    const expandState = {[target]: false};
    storeStateLocally(expandState, 'panelStates');
    // components should be rerendered after panel states changed
    // @ts-expect-error ts-migrate(2683) FIXME: 'this' implicitly has type 'any' because it does n... Remove this comment to see the full error message
    this.setState(expandState);
  };

  expandFilters = () => this.expand('isFiltersCollapsed');

  expandOperations = () => this.expand('isOperationsCollapsed');

  getCurrentPanelState = function () {
    const {
      isFiltersCollapsed = false,
      isOperationsCollapsed = true,
    } = getStateLocally('panelStates');

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
