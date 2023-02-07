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
import {initTranslation} from 'translation';
import * as request from 'request';
import translation from '../../backend/src/main/resources/localization/en.json';
import {setImmediate} from 'timers';

Enzyme.configure({adapter: new Adapter()});
document.execCommand = jest.fn();

beforeAll(async () => {
  jest.spyOn(request, 'get').mockImplementationOnce(async (url) => ({json: () => translation}));
  await initTranslation();
});

global.MutationObserver = class MutationObserver {
  disconnect() {}
  observe() {}
  takeRecords() {
    return [];
  }
};

// since jest does not offer an out of the box way to flush promises:
// https://github.com/facebook/jest/issues/2157
global.flushPromises = () => new Promise((resolve) => setImmediate(resolve));
