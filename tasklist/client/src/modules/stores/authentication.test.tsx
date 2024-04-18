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

import {http, HttpResponse} from 'msw';
import {authenticationStore} from './authentication';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {getStateLocally} from 'modules/utils/localStorage';

describe('authentication store', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should assume that there is an existing session', () => {
    expect(authenticationStore.status).toBe('initial');
  });

  it('should login', async () => {
    nodeMockServer.use(
      http.post('/api/login', () => new HttpResponse(''), {once: true}),
    );

    authenticationStore.disableSession();

    expect(authenticationStore.status).toBe('session-invalid');

    await authenticationStore.handleLogin('demo', 'demo');

    expect(authenticationStore.status).toBe('logged-in');
  });

  it('should handle login failure', async () => {
    nodeMockServer.use(
      http.post('/api/login', () => new HttpResponse('', {status: 401}), {
        once: true,
      }),
    );

    expect(await authenticationStore.handleLogin('demo', 'demo')).toStrictEqual(
      {
        response: null,
        error: {
          variant: 'failed-response',
          response: expect.objectContaining({
            status: 401,
          }),
          networkError: null,
        },
      },
    );
    expect(authenticationStore.status).toBe('initial');
  });

  it('should logout', async () => {
    const mockReload = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      reload: mockReload,
    });
    window.clientConfig = {
      canLogout: true,
      isLoginDelegated: false,
    };

    nodeMockServer.use(
      http.post('/api/login', () => new HttpResponse(''), {once: true}),
      http.post('/api/logout', () => new HttpResponse(''), {once: true}),
    );

    await authenticationStore.handleLogin('demo', 'demo');

    expect(authenticationStore.status).toBe('logged-in');

    await authenticationStore.handleLogout();

    expect(authenticationStore.status).toBe('logged-out');

    expect(mockReload).toHaveBeenCalledTimes(0);
    expect(getStateLocally('wasReloaded')).toBe(false);
  });

  it('should throw an error on logout failure', async () => {
    nodeMockServer.use(
      http.post('/api/logout', () => new HttpResponse('', {status: 500}), {
        once: true,
      }),
    );

    expect(await authenticationStore.handleLogout()).not.toBeUndefined();
  });

  it('should disable session', async () => {
    const mockReload = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      reload: mockReload,
    });
    window.clientConfig = {
      canLogout: true,
      isLoginDelegated: false,
    };

    authenticationStore.activateSession();

    expect(authenticationStore.status).toBe('logged-in');

    authenticationStore.disableSession();

    expect(authenticationStore.status).toBe('session-expired');

    expect(mockReload).toHaveBeenCalledTimes(0);
    expect(getStateLocally('wasReloaded')).toBe(false);

    authenticationStore.activateSession();

    expect(authenticationStore.status).toBe('logged-in');

    expect(mockReload).toHaveBeenCalledTimes(0);
    expect(getStateLocally('wasReloaded')).toBe(false);
  });

  [{canLogout: false}, {canLogout: true, isLoginDelegated: true}].forEach(
    (value) => {
      const {canLogout, isLoginDelegated} = value;

      describe(`when canLogout is ${canLogout} and isLoginDelegated is ${isLoginDelegated}`, () => {
        it('should disable session', async () => {
          const mockReload = vi.fn();
          vi.spyOn(window, 'location', 'get').mockReturnValue({
            ...window.location,
            reload: mockReload,
          });
          window.clientConfig = {
            canLogout,
            isLoginDelegated,
          };

          authenticationStore.activateSession();

          expect(authenticationStore.status).toBe('logged-in');

          authenticationStore.disableSession();

          expect(authenticationStore.status).toBe(
            'invalid-third-party-session',
          );

          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally('wasReloaded')).toBe(true);

          authenticationStore.activateSession();

          expect(authenticationStore.status).toBe('logged-in');

          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally('wasReloaded')).toBe(false);
        });
      });
    },
  );

  [{canLogout: false}, {canLogout: true, isLoginDelegated: true}].forEach(
    (value) => {
      const {canLogout, isLoginDelegated} = value;

      describe(`when canLogout is ${canLogout} and isLoginDelegated is ${isLoginDelegated}`, () => {
        it('should logout', async () => {
          const mockReload = vi.fn();
          vi.spyOn(window, 'location', 'get').mockReturnValue({
            ...window.location,
            reload: mockReload,
          });
          window.clientConfig = {
            canLogout,
            isLoginDelegated,
          };

          nodeMockServer.use(
            http.post('/api/login', () => new HttpResponse(''), {once: true}),
            http.post('/api/logout', () => new HttpResponse(''), {once: true}),
            http.post('/api/login', () => new HttpResponse(''), {once: true}),
          );

          await authenticationStore.handleLogin('demo', 'demo');

          expect(authenticationStore.status).toBe('logged-in');

          await authenticationStore.handleLogout();

          expect(authenticationStore.status).toBe(
            'invalid-third-party-session',
          );

          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally('wasReloaded')).toBe(true);

          await authenticationStore.handleLogin('demo', 'demo');

          expect(authenticationStore.status).toBe('logged-in');

          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally('wasReloaded')).toBe(false);
        });
      });
    },
  );
});
