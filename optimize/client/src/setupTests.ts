/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import 'raf/polyfill';
import Enzyme from 'enzyme';
import Adapter from '@cfaester/enzyme-adapter-react-18';
import 'jest-enzyme';
import './modules/polyfills/array_flat';
import {setTranslation} from './modules/translation';
import translation from '../../../optimize/backend/src/main/resources/localization/en.json';
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
