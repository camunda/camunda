/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';
import {DataManager as DataManagerClass} from './core';

const DataContext = React.createContext({});

function withData(Component) {
  function WithData(props) {
    return (
      <DataContext.Consumer>
        {contextValue => <Component {...props} {...contextValue} />}
      </DataContext.Consumer>
    );
  }

  withData.WrappedComponent = Component;

  withData.displayName = `WithModal(${Component.displayName ||
    Component.name ||
    'Component'})`;

  return WithData;
}

function DataManager(props) {
  const DataManager = new DataManagerClass(LOADING_STATE, SUBSCRIPTION_TOPIC);

  return (
    <DataContext.Provider
      value={{
        DataManager
      }}
    >
      {props.children}
    </DataContext.Provider>
  );
}

DataManager.propTypes = {
  children: PropTypes.object
};

export {DataManager, withData};
