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
import {mockServer} from 'modules/mock-server/node';

// configure enzyme
Enzyme.configure({adapter: new Adapter()});

class MockJSONEditor {
  updateText() {}
  destroy() {}
  set() {}
  get() {}
}

jest.mock('jsoneditor', () => MockJSONEditor);
jest.mock('jsoneditor/dist/jsoneditor.css', () => undefined);
jest.mock('brace/theme/tomorrow_night', () => undefined);
jest.mock('brace/theme/tomorrow', () => undefined);
jest.mock('modules/utils/date/formatDate');
jest.mock('@camunda-cloud/common-ui-react', () => {
  const React = require('react');

  return {
    ...jest.requireActual('@camunda-cloud/common-ui-react'),
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

const localStorageMock = (function () {
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

Object.defineProperty(window, 'localStorage', {value: localStorageMock});

window.MutationObserver = MutationObserver;

beforeAll(() => mockServer.listen());
afterEach(() => mockServer.resetHandlers());
afterAll(() => mockServer.close());
