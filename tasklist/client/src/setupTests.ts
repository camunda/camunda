/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import '@testing-library/jest-dom/vitest';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {configure} from 'common/testing/testing-library';
import {reactQueryClient} from 'common/react-query/reactQueryClient';
import en from 'common/i18n/locales/en.json';
import i18n, {t} from 'i18next';

function initTestI18next() {
  i18n.init({
    lng: 'en',
    resources: {
      en,
    },
    interpolation: {
      escapeValue: false,
    },
  });
}

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
initTestI18next();

beforeEach(() => {
  mockMatchMedia();

  window.localStorage.clear();

  vi.stubGlobal('Notification', {permission: 'denied'});
});

beforeAll(() => {
  nodeMockServer.listen({
    onUnhandledRequest: 'error',
  });

  vi.mock('react-i18next', () => ({
    Trans: ({children}: {children: React.ReactNode}) => children,
    useTranslation: () => {
      return {
        t,
        i18n: {
          changeLanguage: () => new Promise<void>(() => {}),
          resolvedLanguage: 'en',
        },
      };
    },
    initReactI18next: {
      type: '3rdParty',
      init: () => {},
    },
  }));

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

  // temporary fix while jsdom doesn't implement this: https://github.com/jsdom/jsdom/issues/1695
  window.HTMLElement.prototype.scrollIntoView = function () {};
});

afterEach(() => {
  reactQueryClient.clear();

  nodeMockServer.resetHandlers();
});

afterAll(() => {
  nodeMockServer.close();
});

configure({
  asyncUtilTimeout: 7000,
});
