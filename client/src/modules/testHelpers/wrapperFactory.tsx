/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

export const wrapperFactory = (wrappers = [], WrappedComponent: any) => {
  return wrappers.reverse().reduce((acc, Wrapper) => {
    if (Object.keys(Wrapper).includes('Wrapper')) {
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'Wrapper' does not exist on type 'never'.
      return <Wrapper.Wrapper {...Wrapper.props}>{acc}</Wrapper.Wrapper>;
    } else {
      // @ts-expect-error ts-migrate(2604) FIXME: JSX element type 'Wrapper' does not have any const... Remove this comment to see the full error message
      return <Wrapper>{acc}</Wrapper>;
    }
  }, WrappedComponent);
};

export const mountWrappedComponent = (
  wrappers = [],
  Component: any,
  props: any
) => {
  return mount(wrapperFactory(wrappers, <Component {...props} />));
};
