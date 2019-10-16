/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

export const wrapperFactory = (wrappers = [], WrappedComponent) => {
  return wrappers.reverse().reduce((acc, Wrapper) => {
    if (Object.keys(Wrapper).includes('Wrapper')) {
      return <Wrapper.Wrapper {...Wrapper.props}>{acc}</Wrapper.Wrapper>;
    } else {
      return <Wrapper>{acc}</Wrapper>;
    }
  }, WrappedComponent);
};

export const mountWrappedComponent = (wrappers = [], Component, props) => {
  return mount(wrapperFactory(wrappers, <Component {...props} />));
};
