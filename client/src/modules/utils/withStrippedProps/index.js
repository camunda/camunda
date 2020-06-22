/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

const withStrippedProps = (propsToRemove) => (Component) => {
  function strippedComponent(props) {
    const strippedProps = Object.entries(props)
      .filter(([key]) => !propsToRemove.includes(key))
      .reduce((stripped, [key, value]) => ({...stripped, [key]: value}), {});

    return <Component {...strippedProps} />;
  }

  strippedComponent.displayName = `withStrippedProps(${
    Component.displayName || Component.name || 'Component'
  })`;

  strippedComponent.WrappedComponent = Component;

  return strippedComponent;
};

export default withStrippedProps;
