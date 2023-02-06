/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

// jest-dom adds custom jest matchers for asserting on DOM nodes.
// allows you to do things like:
// expect(element).toHaveTextContent(/react/i)
// learn more: https://github.com/testing-library/jest-dom
import '@testing-library/jest-dom/extend-expect';
import {clearClientCache} from 'modules/apollo-client';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {configure} from '@testing-library/react';
import {DEFAULT_MOCK_CLIENT_CONFIG} from 'modules/mocks/window';

function mockMatchMedia() {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation((query) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: jest.fn(),
      removeListener: jest.fn(),
      addEventListener: jest.fn(),
      removeEventListener: jest.fn(),
      dispatchEvent: jest.fn(),
    })),
  });
}

mockMatchMedia();

beforeEach(() => {
  mockMatchMedia();
});

beforeAll(() => {
  nodeMockServer.listen({
    onUnhandledRequest: 'error',
  });

  Object.defineProperty(window, 'clientConfig', {
    writable: true,
    value: DEFAULT_MOCK_CLIENT_CONFIG,
  });
});

afterEach(async () => {
  nodeMockServer.resetHandlers();
  await clearClientCache();
});

afterAll(() => nodeMockServer.close());

// mock app version
process.env.REACT_APP_VERSION = '1.2.3';

jest.mock('@bpmn-io/form-js-viewer/dist/assets/form-js.css', () => undefined);

configure({
  asyncUtilTimeout: 7000,
});
