/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

// using mount to support the useEffect lifecycle
// see: https://github.com/enzymejs/enzyme/issues/2086
import {mount} from 'enzyme';

import {Logout} from './Logout';
import {get} from 'request';
import {addNotification} from 'notifications';

jest.mock('request', () => ({get: jest.fn()}));
jest.mock('notifications', () => ({addNotification: jest.fn()}));

const flushPromises = () => new Promise((resolve) => setImmediate(resolve));

const props = {
  mightFail: jest.fn(),
  history: {replace: jest.fn()},
};

it('should logout from server', () => {
  mount(<Logout {...props} />);

  expect(get).toHaveBeenCalledWith('api/authentication/logout');
});

it('should redirect to the index page', async () => {
  props.history.replace.mockClear();
  mount(<Logout {...props} mightFail={(_, cb) => cb()} />);

  await flushPromises();

  expect(props.history.replace).toHaveBeenCalledWith('/');
});

it('should show an error if the logout fails', async () => {
  props.history.replace.mockClear();
  addNotification.mockClear();
  mount(<Logout {...props} mightFail={(_, cb, fail) => fail()} />);

  await flushPromises();

  expect(props.history.replace).toHaveBeenCalledWith('/');
  expect(addNotification).toHaveBeenCalled();
  expect(addNotification.mock.calls[0][0].type).toBe('error');
});
