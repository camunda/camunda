/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {runLastEffect} from 'react';

import {shallow} from 'enzyme';

import {Logout} from './Logout';
import {post} from 'request';
import {addNotification} from 'notifications';

jest.mock('request', () => ({post: jest.fn()}));
jest.mock('notifications', () => ({addNotification: jest.fn()}));

const props = {
  mightFail: jest.fn(),
  history: {replace: jest.fn()},
};

let originalLocation;

beforeEach(() => {
  originalLocation = window.location;
  Object.defineProperty(window, 'location', {configurable: true, value: {href: ''}});
});

afterEach(() => {
  Object.defineProperty(window, 'location', {configurable: true, value: originalLocation});
});

it('should logout via the CSL /logout endpoint', () => {
  shallow(<Logout {...props} />);
  runLastEffect();

  expect(post).toHaveBeenCalledWith('/logout');
});

it('should navigate to the IdP end-session url on a 200 response', async () => {
  const response = {status: 200, json: async () => ({url: 'http://idp/end-session'})};
  shallow(<Logout {...props} mightFail={(_, cb) => cb(response)} />);
  runLastEffect();

  await flushPromises();

  expect(window.location.href).toBe('http://idp/end-session');
});

it('should navigate to the root on a 204 response', async () => {
  const response = {status: 204};
  shallow(<Logout {...props} mightFail={(_, cb) => cb(response)} />);
  runLastEffect();

  await flushPromises();

  expect(window.location.href).toBe('/');
});

it('should show an error and go to the index page if the logout fails', async () => {
  props.history.replace.mockClear();
  addNotification.mockClear();
  shallow(<Logout {...props} mightFail={(_, _cb, fail) => fail()} />);
  runLastEffect();

  await flushPromises();

  expect(props.history.replace).toHaveBeenCalledWith('/');
  expect(addNotification).toHaveBeenCalled();
  expect(addNotification.mock.calls[0][0].type).toBe('error');
});
