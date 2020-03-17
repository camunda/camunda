/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import withSharedState from './withSharedState';

const Component = () => {};

describe('withSharedState', () => {
  let node;

  beforeEach(() => {
    const Wrapped = withSharedState(Component);
    node = shallow(<Wrapped customProp="1234" />);
  });

  it('should contain the component with additional props', () => {
    const component = node.find(Component);

    expect(component).toExist();
    expect(component.prop('customProp')).toBe('1234');
    expect(component.prop('storeStateLocally')).toBeDefined();
    expect(component.prop('clearStateLocally')).toBeDefined();
    expect(component.prop('getStateLocally')).toBeDefined();
  });

  it('should store state in localstorage', () => {
    const data = {a: 1, b: 2};

    node.instance().storeStateLocally(data);

    expect(node.instance().getStateLocally()).toEqual(data);
  });

  it('should retrieve localstorage state', () => {
    node.instance().storeStateLocally({a: 1, b: 2});
    expect(node.instance().getStateLocally()).toEqual({a: 1, b: 2});
  });

  it('should clear localstorage state', () => {
    localStorage.removeItem.mockClear();

    node.instance().clearStateLocally();

    expect(localStorage.removeItem).toHaveBeenCalledWith('sharedState');
  });
});
