/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

const CollapsablePanelContext = React.createContext();

// Wrapper that passes the theme as a prop
const CollapsablePanelConsumer = CollapsablePanelContext.Consumer;

// Top level component to pass down theme in the App
class CollapsablePanelProvider extends React.Component {
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  // we start with both panels not collapsed
  state = {isFiltersCollapsed: false, isOperationsCollapsed: true};

  toggle = target =>
    this.setState(prevState => {
      return {[target]: !prevState[target]};
    });

  toggleFilters = () => this.toggle('isFiltersCollapsed');

  toggledOperations = () => this.toggle('isOperationsCollapsed');

  expand = target => this.setState({[target]: false});

  expandFilters = () => this.expand('isFiltersCollapsed');

  expandOperations = () => this.expand('isOperationsCollapsed');

  render() {
    const contextValue = {
      ...this.state,
      toggleFilters: this.toggleFilters,
      toggledOperations: this.toggledOperations,
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

export {
  CollapsablePanelConsumer,
  CollapsablePanelProvider,
  withCollapsablePanel
};
