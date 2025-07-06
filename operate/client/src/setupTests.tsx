/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import '@testing-library/jest-dom';
import {mockServer} from 'modules/mock-server/node';
import {configure} from 'modules/testing-library';
import React from 'react';

vi.mock('@devbookhq/splitter', async () => {
  const actual = await vi.importActual('@devbookhq/splitter');
  const mockModule = await import('./__mocks__/@devbookhq/splitter');
  return {
    ...actual,
    default: mockModule.default,
    SplitDirection: mockModule.SplitDirection,
  };
});

vi.mock('modules/components/MonacoEditor');

vi.mock('modules/components/InfiniteScroller', () => {
  const InfiniteScroller: React.FC<{children?: React.ReactNode}> = ({
    children,
  }) => {
    return <>{children}</>;
  };
  return {InfiniteScroller};
});

vi.mock('modules/stores/licenseTag', () => ({
  licenseTagStore: {
    fetchLicense: vi.fn(),
    state: {isTagVisible: false},
  },
}));

vi.mock('bpmn-js/lib/features/outline', () => {
  return () => {};
});

vi.mock('@floating-ui/react-dom', () => {
  const originalModule = vi.importActual('@floating-ui/react-dom');

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
    removeItem: vi.fn(),
  };
})();

beforeAll(() =>
  mockServer.listen({
    onUnhandledRequest: 'error',
  }),
);
afterEach(() => mockServer.resetHandlers());
afterAll(() => mockServer.close());
beforeEach(async () => {
  vi.stubGlobal(
    'ResizeObserver',
    (await import('resize-observer-polyfill')).default,
  );
  vi.stubGlobal('localStorage', localStorageMock);
  vi.stubGlobal('MutationObserver', MutationObserver);
  vi.stubGlobal('clientConfig', {
    canLogout: true,
  });
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation(() => ({
      matches: false,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );

  // Clear localStorage for each test
  localStorage.clear();
});

configure({
  asyncUtilTimeout: 7000,
});
