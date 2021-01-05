/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import Enzyme from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';
import 'jest-enzyme';
import 'jest-styled-components';
import '@testing-library/jest-dom';
// @ts-expect-error
import MutationObserver from '@sheerun/mutationobserver-shim';
import {mockServer} from 'modules/mockServer';

// configure enzyme
Enzyme.configure({adapter: new Adapter()});

// mock date util
jest.mock('modules/utils/date/formatDate');
jest.mock('@camunda-cloud/common-ui-react', () => {
  const React = require('react');

  return {
    CmNotificationContainer: React.forwardRef(
      function CmNotificationContainer() {
        return null;
      }
    ),
  };
});

global.beforeEach(() => {
  localStorage.clear();

  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation(() => ({
      matches: false,
    })),
  });
});

// @ts-expect-error
global.localStorage = (function () {
  let store: {[key: string]: string} = {};
  return {
    getItem(key: string) {
      return store[key];
    },
    setItem(key: string, value: string) {
      store[key] = value;
    },
    clear() {
      store = {};
    },
    removeItem: jest.fn(),
  };
})();

window.MutationObserver = MutationObserver;

beforeAll(() =>
  mockServer.listen({
    onUnhandledRequest: 'warn',
  })
);
afterEach(() => mockServer.resetHandlers());
afterAll(() => mockServer.close());
