/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

export default function withSharedState(Component) {
  class WithSharedState extends React.Component {
    storeStateLocally = (state, storageKey) => {
      const current = JSON.parse(
        localStorage.getItem(storageKey || 'sharedState') || '{}'
      );

      localStorage.setItem(
        storageKey || 'sharedState',
        JSON.stringify({...current, ...state})
      );
    };

    clearStateLocally = () => {
      localStorage.removeItem('sharedState');
    };

    getStateLocally = storageKey => {
      return JSON.parse(
        localStorage.getItem(storageKey || 'sharedState') || '{}'
      );
    };

    render() {
      return (
        <Component
          storeStateLocally={this.storeStateLocally}
          getStateLocally={this.getStateLocally}
          clearStateLocally={this.clearStateLocally}
          {...this.props}
        />
      );
    }
  }

  WithSharedState.displayName = `${Component.displayName ||
    Component.name ||
    'Component'}SharedState`;

  WithSharedState.WrappedComponent = Component;

  return WithSharedState;
}
