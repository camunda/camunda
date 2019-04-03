/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import Async from './Async';

import {mount} from 'enzyme';

it('should call the function when the promise is fulfilled', done => {
  const node = mount(<Async.React.Component />);

  const cb = jest.fn();
  node.instance().await(Promise.resolve('value'), cb);

  setTimeout(() => {
    expect(cb).toHaveBeenCalledWith('value');
    done();
  });
});

it('should not call the function when await is canceled', done => {
  const node = mount(<Async.React.Component />);

  const cb = jest.fn();
  node.instance().await(Promise.resolve('value'), cb);
  node.instance().cancelAwait();

  setTimeout(() => {
    expect(cb).not.toHaveBeenCalledWith('value');
    done();
  });
});

it('should propagate errors', done => {
  const node = mount(<Async.React.Component />);

  const cb = jest.fn();
  let error = null;
  (async () => {
    try {
      await node.instance().await(Promise.reject('value'), cb);
    } catch (e) {
      error = e;
    }
  })();

  setTimeout(() => {
    expect(cb).not.toHaveBeenCalledWith('value');
    expect(error).toBe('value');
    done();
  });
});
