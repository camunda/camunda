/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
