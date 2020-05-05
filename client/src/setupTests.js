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
import {configure} from '@testing-library/dom';
import MutationObserver from '@sheerun/mutationobserver-shim';

// see https://github.com/mobxjs/mobx-react-lite/#observer-batching
import 'mobx-react-lite/batchingForReactDom';

// configure enzyme
Enzyme.configure({adapter: new Adapter()});

// mock date util
jest.mock('modules/utils/date/formatDate');
global.beforeEach(() => {
  localStorage.clear();

  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation((query) => ({
      matches: false,
    })),
  });
});

global.localStorage = (function () {
  var store = {};
  return {
    getItem: function (key) {
      return store[key];
    },
    setItem: function (key, value) {
      store[key] = value.toString();
    },
    clear: function () {
      store = {};
    },
    removeItem: jest.fn(),
  };
})();

window.MutationObserver = MutationObserver;

configure({
  testIdAttribute: 'data-test',
});
