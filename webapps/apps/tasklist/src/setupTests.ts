/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import '@testing-library/jest-dom/vitest';
import {cleanup} from '@testing-library/react';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {configure} from 'modules/testing-library';
import {DEFAULT_MOCK_CLIENT_CONFIG} from 'modules/mocks/window';
import {reactQueryClient} from 'modules/react-query/reactQueryClient';

function mockMatchMedia() {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

mockMatchMedia();

beforeEach(() => {
  mockMatchMedia();

  window.localStorage.clear();

  vi.stubGlobal('Notification', {permission: 'denied'});
});

beforeAll(() => {
  nodeMockServer.listen({
    onUnhandledRequest: 'error',
  });

  Object.defineProperty(window, 'clientConfig', {
    writable: true,
    value: DEFAULT_MOCK_CLIENT_CONFIG,
  });

  Object.defineProperty(window, 'localStorage', {
    value: (function () {
      let store: Record<string, unknown> = {};

      return {
        getItem(key: string) {
          return store[key] ?? null;
        },

        setItem(key: string, value: unknown) {
          store[key] = value;
        },

        clear() {
          store = {};
        },

        removeItem(key: string) {
          delete store[key];
        },

        getAll() {
          return store;
        },
      };
    })(),
  });
});

afterEach(() => {
  cleanup();
  reactQueryClient.clear();
  window.clientConfig = DEFAULT_MOCK_CLIENT_CONFIG;
  nodeMockServer.resetHandlers();
});

afterAll(() => {
  nodeMockServer.close();
});

configure({
  asyncUtilTimeout: 7000,
});
