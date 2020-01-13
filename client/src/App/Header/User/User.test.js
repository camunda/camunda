/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {
  mockResolvedAsyncFn,
  mockRejectedAsyncFn,
  flushPromises
} from 'modules/testUtils';

import useLocalStorage from 'modules/hooks/useLocalStorage';

import User from './User';
import * as api from 'modules/api/header/header';

import * as Styled from './styled';

jest.mock('modules/hooks/useLocalStorage');
jest.mock('modules/utils/bpmn');

useLocalStorage.mockImplementation(() => {
  return {
    storedValue: mockUser,
    setLocalState: jest.fn()
  };
});

const defaultConsoleError = console.error;
const defaultConsoleLog = console.log;
const mockUser = {
  firstname: 'foo',
  lastname: 'bar'
};
const mockSsoUser = {
  firstname: null,
  lastname: 'foo bar'
};

describe('User', () => {
  beforeEach(() => {
    console.error = jest.fn();
    console.log = jest.fn();
  });

  afterEach(() => {
    console.error = defaultConsoleError;
    console.log = defaultConsoleLog;
    useLocalStorage.mockClear();
  });

  it('renders with locally stored User data', async () => {
    useLocalStorage.mockImplementation(() => {
      return {
        storedValue: mockUser,
        setLocalState: jest.fn()
      };
    });
    const node = mount(
      <ThemeProvider>
        <User />
      </ThemeProvider>
    );

    expect(node.find('User').text()).toContain(
      `${(mockUser.firstname, ' ', mockUser.lastname)}`
    );
  });

  it('renders with User data', async () => {
    api.fetchUser = mockResolvedAsyncFn(mockUser);
    const node = mount(
      <ThemeProvider>
        <User />
      </ThemeProvider>
    );

    await flushPromises();
    node.update();

    expect(node.find(Styled.Dropdown).text()).toContain(mockUser.firstname);
    expect(node.find(Styled.Dropdown).text()).toContain(mockUser.lastname);
  });

  it('renders with SSO User data', async () => {
    api.fetchUser = mockResolvedAsyncFn(mockSsoUser);
    const node = mount(
      <ThemeProvider>
        <User />
      </ThemeProvider>
    );

    await flushPromises();
    node.update();

    expect(node.find(Styled.Dropdown).text()).toContain(mockSsoUser.lastname);
  });

  it('renders without User data', async () => {
    useLocalStorage.mockImplementation(() => {
      return {
        storedValue: {},
        setLocalStorage: jest.fn()
      };
    });
    api.fetchUser = mockRejectedAsyncFn();

    const node = mount(
      <ThemeProvider>
        <User />
      </ThemeProvider>
    );

    expect(node.find(Styled.SkeletonBlock)).toExist();
  });

  it('should handle logout', async () => {
    useLocalStorage.mockImplementation(() => {
      return {
        storedValue: {},
        setLocalStorage: jest.fn()
      };
    });
    api.fetchUser = mockResolvedAsyncFn(mockUser);
    api.logout = mockResolvedAsyncFn();

    const node = mount(
      <ThemeProvider>
        <User handleRedirect={() => {}} />
      </ThemeProvider>
    );

    await flushPromises();
    node.update();

    node.find('button').simulate('click');
    node.update();

    expect(node.find('[data-test="logout-button"]')).toExist();
    node.find('[data-test="logout-button"]').simulate('click');
    node.update();

    expect(api.logout).toHaveBeenCalled();
  });
});
