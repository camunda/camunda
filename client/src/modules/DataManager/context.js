/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {DataManager} from './core/';

const DataContext = React.createContext({});

function withData(Component) {
  function WithData(props) {
    return (
      <DataContext.Consumer>
        {({dataManager}) => <Component {...props} dataManager={dataManager} />}
      </DataContext.Consumer>
    );
  }

  withData.WrappedComponent = Component;

  withData.displayName = `WithModal(${Component.displayName ||
    Component.name ||
    'Component'})`;

  return WithData;
}

function DataManagerProvider(props) {
  const dataManager = new DataManager();

  return (
    <DataContext.Provider value={{dataManager}}>
      {props.children}
    </DataContext.Provider>
  );
}

DataManagerProvider.propTypes = {
  children: PropTypes.object
};

export {DataManagerProvider, withData};
