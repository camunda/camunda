/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import withSharedState from 'modules/components/withSharedState';

const CollapsablePanelContext = React.createContext();

// Wrapper that passes the theme as a prop
const CollapsablePanelConsumer = CollapsablePanelContext.Consumer;

// Top level component to pass down theme in the App
class CollapsablePanelProvider extends React.Component {
  static propTypes = {
    getStateLocally: PropTypes.func.isRequired,
    storeStateLocally: PropTypes.func.isRequired,
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  // we start with both panels not collapsed
  state = {isFiltersCollapsed: false, isOperationsCollapsed: true};

  toggle = function(target) {
    const currentPanelState = this.getCurrentPanelState();
    const expandState = {[target]: !currentPanelState[target]};

    this.props.storeStateLocally(expandState, 'panelStates');
    // components should be rerendered after panel states changed
    this.setState(expandState);
  };

  toggleFilters = () => this.toggle('isFiltersCollapsed');

  toggleOperations = () => this.toggle('isOperationsCollapsed');

  expand = function(target) {
    const expandState = {[target]: false};
    this.props.storeStateLocally(expandState, 'panelStates');
    // components should be rerendered after panel states changed
    this.setState(expandState);
  };

  expandFilters = () => this.expand('isFiltersCollapsed');

  expandOperations = () => this.expand('isOperationsCollapsed');

  getCurrentPanelState = function() {
    const {
      isFiltersCollapsed = false,
      isOperationsCollapsed = true
    } = this.props.getStateLocally('panelStates');

    return {
      isFiltersCollapsed,
      isOperationsCollapsed
    };
  };

  render() {
    const currentPanelState = this.getCurrentPanelState();

    const contextValue = {
      ...currentPanelState,
      toggleFilters: this.toggleFilters,
      toggleOperations: this.toggleOperations,
      expandFilters: this.expandFilters,
      expandOperations: this.expandOperations
    };

    return (
      <CollapsablePanelContext.Provider value={contextValue}>
        {this.props.children}
      </CollapsablePanelContext.Provider>
    );
  }
}

const withCollapsablePanel = Component => {
  function WithCollapsablePanel(props) {
    return (
      <CollapsablePanelConsumer>
        {contextValue => <Component {...props} {...contextValue} />}
      </CollapsablePanelConsumer>
    );
  }

  WithCollapsablePanel.WrappedComponent = Component;

  WithCollapsablePanel.displayName = `WithCollapsablePanel(${Component.displayName ||
    Component.name ||
    'Component'})`;

  return WithCollapsablePanel;
};

const CollapsablePanelProviderWithSharedState = withSharedState(
  CollapsablePanelProvider
);
export {
  CollapsablePanelConsumer,
  CollapsablePanelProviderWithSharedState as CollapsablePanelProvider,
  withCollapsablePanel
};
