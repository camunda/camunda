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
import {get, post} from 'request';
import {addNotification} from 'notifications';
import {isCslEnabled} from 'config';

jest.mock('request', () => ({get: jest.fn(), post: jest.fn()}));
jest.mock('notifications', () => ({addNotification: jest.fn()}));
jest.mock('config', () => ({isCslEnabled: jest.fn()}));

const props = {
  mightFail: jest.fn(),
  history: {replace: jest.fn()},
};

let originalLocation;

beforeEach(() => {
  jest.clearAllMocks();
  isCslEnabled.mockResolvedValue(false);
  originalLocation = window.location;
  Object.defineProperty(window, 'location', {configurable: true, value: {href: ''}});
});

afterEach(() => {
  Object.defineProperty(window, 'location', {configurable: true, value: originalLocation});
});

describe('legacy logout (CSL disabled)', () => {
  it('should logout from the server', async () => {
    shallow(<Logout {...props} />);
    runLastEffect();

    await flushPromises();

    expect(get).toHaveBeenCalledWith('api/authentication/logout');
  });

  it('should redirect to the index page', async () => {
    shallow(<Logout {...props} mightFail={(_, cb) => cb()} />);
    runLastEffect();

    await flushPromises();

    expect(props.history.replace).toHaveBeenCalledWith('/');
  });

  it('should show an error and go to the index page if the logout fails', async () => {
    shallow(<Logout {...props} mightFail={(_, _cb, fail) => fail()} />);
    runLastEffect();

    await flushPromises();

    expect(props.history.replace).toHaveBeenCalledWith('/');
    expect(addNotification).toHaveBeenCalled();
    expect(addNotification.mock.calls[0][0].type).toBe('error');
  });
});

describe('CSL logout (CSL enabled)', () => {
  beforeEach(() => {
    isCslEnabled.mockResolvedValue(true);
  });

  it('should logout via the CSL /logout endpoint', async () => {
    shallow(<Logout {...props} />);
    runLastEffect();

    await flushPromises();

    expect(post).toHaveBeenCalledWith('logout');
    expect(get).not.toHaveBeenCalled();
  });

  it('should navigate to the IdP end-session url on a 200 response', async () => {
    const response = {status: 200, json: async () => ({url: 'http://idp/end-session'})};
    shallow(<Logout {...props} mightFail={(_, cb) => cb(response)} />);
    runLastEffect();

    await flushPromises();

    expect(window.location.href).toBe('http://idp/end-session');
  });

  it('should fall back to the app root on a 200 response without a url', async () => {
    const response = {status: 200, json: async () => ({})};
    shallow(<Logout {...props} mightFail={(_, cb) => cb(response)} />);
    runLastEffect();

    await flushPromises();

    expect(window.location.href).toBe('.');
  });

  it('should fall back to the app root on a 200 response with an unparseable body', async () => {
    const response = {
      status: 200,
      json: async () => {
        throw new Error('not json');
      },
    };
    shallow(<Logout {...props} mightFail={(_, cb) => cb(response)} />);
    runLastEffect();

    await flushPromises();

    expect(window.location.href).toBe('.');
  });

  it('should navigate to the app root on a 204 response', async () => {
    const response = {status: 204};
    shallow(<Logout {...props} mightFail={(_, cb) => cb(response)} />);
    runLastEffect();

    await flushPromises();

    expect(window.location.href).toBe('.');
  });

  it('should show an error and go to the index page if the logout fails', async () => {
    shallow(<Logout {...props} mightFail={(_, _cb, fail) => fail()} />);
    runLastEffect();

    await flushPromises();

    expect(props.history.replace).toHaveBeenCalledWith('/');
    expect(addNotification).toHaveBeenCalled();
    expect(addNotification.mock.calls[0][0].type).toBe('error');
  });
});
