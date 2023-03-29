/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import 'jest-styled-components';
import '@testing-library/jest-dom';
import {mockServer} from 'modules/mock-server/node';
import {configure} from 'modules/testing-library';
import React from 'react';
import {Textfield as MockTextfield} from 'modules/mocks/common-ui/Textfield';
import {Checkbox as MockCheckbox} from 'modules/mocks/common-ui/Checkbox';
import {Select as MockSelect} from 'modules/mocks/common-ui/Select';
import {Dropdown as MockDropdown} from 'modules/mocks/common-ui/Dropdown';
import MockSplitter from 'modules/mocks/Splitter';
import {Text as MockText} from 'modules/mocks/common-ui/Text';
import {CheckboxGroup as MockCheckboxGroup} from 'modules/mocks/common-ui/CheckboxGroup';
import {Icon as MockIcon} from 'modules/mocks/common-ui/Icon';
import {Button as MockButton} from 'modules/mocks/common-ui/Button';

global.ResizeObserver = require('resize-observer-polyfill');

jest.mock('@devbookhq/splitter', () => {
  return {
    __esModule: true,
    ...jest.requireActual('@devbookhq/splitter'),
    default: MockSplitter,
  };
});
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
    CmTextfield: MockTextfield,
    CmCheckbox: MockCheckbox,
    CmSelect: MockSelect,
    CmDropdown: MockDropdown,
    CmText: MockText,
    CmCheckboxGroup: MockCheckboxGroup,
    CmIcon: MockIcon,
    CmButton: MockButton,
  };
});

jest.mock('modules/components/InfiniteScroller', () => {
  const InfiniteScroller: React.FC<{children?: React.ReactNode}> = ({
    children,
  }) => {
    return <>{children}</>;
  };
  return {InfiniteScroller};
});

global.beforeEach(() => {
  localStorage.clear();

  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation(() => ({
      matches: false,
      addListener: jest.fn(),
      removeListener: jest.fn(),
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
    })),
  });
});

jest.mock('@floating-ui/react-dom', () => {
  const originalModule = jest.requireActual('@floating-ui/react-dom');

  return {
    ...originalModule,
    hide: () => {},
  };
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
beforeEach(() => {
  Object.defineProperty(window, 'clientConfig', {
    value: {
      canLogout: true,
    },
    writable: true,
  });
});

configure({
  asyncUtilTimeout: 7000,
});
