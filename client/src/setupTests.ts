/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import 'raf/polyfill';
import Enzyme from 'enzyme';
import Adapter from '@wojtekmaj/enzyme-adapter-react-17';
import 'jest-enzyme';
import './modules/polyfills/array_flat';
import {setTranslation} from 'translation';
import translation from '../../backend/src/main/resources/localization/en.json';
import {setImmediate} from 'timers';

Enzyme.configure({adapter: new Adapter()});
document.execCommand = jest.fn();

beforeAll(() => {
  setTranslation(translation);
});

global.MutationObserver = class MutationObserver {
  observe() {}
  disconnect() {}
  takeRecords(): MutationRecord[] {
    return [];
  }
};

declare global {
  function flushPromises(): Promise<void>;
}
// since jest does not offer an out of the box way to flush promises:
// https://github.com/facebook/jest/issues/2157
global.flushPromises = (): Promise<void> => new Promise((resolve) => setImmediate(resolve));

// cleans pending promises and mock call history to isolate tests from each other
afterEach(async () => {
  await flushPromises();
  jest.clearAllMocks();
});

// this calls garbage collection after each test suite to free memory
// due to memory leaks in jest with node 16 https://github.com/facebook/jest/issues/11956
afterAll(() => {
  if (global.gc) {
    global.gc();
  }
});
