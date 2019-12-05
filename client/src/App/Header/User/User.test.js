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

import User from './User';
import * as api from 'modules/api/header/header';

import * as Styled from './styled';

jest.mock('modules/utils/bpmn');

const defaultConsoleError = console.error;
const defaultConsoleLog = console.log;
const mockUser = {
  firstname: 'foo',
  lastname: 'bar'
};

describe('User', () => {
  beforeEach(() => {
    console.error = jest.fn();
    console.log = jest.fn();
  });

  afterEach(() => {
    console.error = defaultConsoleError;
    console.log = defaultConsoleLog;
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

  it('renders without User data', async () => {
    api.fetchUser = mockRejectedAsyncFn();

    const node = mount(
      <ThemeProvider>
        <User />
      </ThemeProvider>
    );

    expect(node.find(Styled.SkeletonBlock)).toExist();
    expect(node.find(Styled.Circle)).toExist();
  });

  it('should handle logout', async () => {
    api.fetchUser = mockResolvedAsyncFn(mockUser);
    api.logout = mockResolvedAsyncFn();

    const node = mount(
      <ThemeProvider>
        <User />
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
