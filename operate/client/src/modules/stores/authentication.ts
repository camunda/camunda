/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {makeObservable, observable, action, computed} from 'mobx';
import {getUser, UserDto} from 'modules/api/getUser';
import {login, Credentials} from 'modules/api/login';
import {logout} from 'modules/api/logout';
import {NetworkError} from 'modules/networkError';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

type Permissions = Array<'read' | 'write'>;

type State = {
  status:
    | 'initial'
    | 'logged-in'
    | 'fetching-user-information'
    | 'user-information-fetched'
    | 'logged-out'
    | 'session-expired'
    | 'invalid-initial-session'
    | 'invalid-third-party-session';
  permissions: Permissions;
  displayName: string | null;
  canLogout: boolean;
  userId: string | null;
  salesPlanType: UserDto['salesPlanType'];
  roles: ReadonlyArray<string> | null;
  c8Links: UserDto['c8Links'];
  tenants: UserDto['tenants'];
};

const DEFAULT_STATE: State = {
  status: 'initial',
  permissions: ['read', 'write'],
  displayName: null,
  canLogout: false,
  userId: null,
  salesPlanType: null,
  roles: [],
  c8Links: {},
  tenants: null,
};

class Authentication {
  state: State = {...DEFAULT_STATE};
  constructor() {
    makeObservable(this, {
      state: observable,
      disableSession: action,
      expireSession: action,
      startLoadingUser: action,
      setUser: action,
      reset: action,
      resetUser: action,
      setStatus: action,
      endLogin: action,
      tenantsById: computed,
    });
  }

  disableSession = () => {
    this.resetUser();

    if (
      !window.clientConfig?.canLogout ||
      window.clientConfig?.isLoginDelegated
    ) {
      this.#handleThirdPartySessionExpiration();

      return;
    }

    this.setStatus('logged-out');
  };

  expireSession = () => {
    this.resetUser();

    if (
      !window.clientConfig?.canLogout ||
      window.clientConfig?.isLoginDelegated
    ) {
      this.#handleThirdPartySessionExpiration();

      return;
    }

    if (this.state.status === 'user-information-fetched') {
      this.setStatus('session-expired');

      return;
    }

    this.setStatus('invalid-initial-session');
  };

  #handleThirdPartySessionExpiration = () => {
    const wasReloaded = getStateLocally()?.wasReloaded;

    this.setStatus('invalid-third-party-session');

    if (wasReloaded) {
      return;
    }

    storeStateLocally({
      wasReloaded: true,
    });

    window.location.reload();
  };

  handleLogin = async (credentials: Credentials): Promise<Error | void> => {
    const response = await login(credentials);

    if (!response.isSuccess) {
      return new NetworkError(
        'Could not login credentials',
        response.statusCode,
      );
    }

    this.endLogin();

    return;
  };

  endLogin = () => {
    this.state.status = 'logged-in';
  };

  authenticate = async (): Promise<void | Error> => {
    this.startLoadingUser();

    const response = await getUser({
      onFailure: () => {
        this.expireSession();
      },
      onException: () => {
        this.disableSession();
      },
    });

    if (!response.isSuccess) {
      return new Error('Could not fetch user information');
    }

    this.setUser(response.data);
  };

  startLoadingUser = () => {
    this.state.status = 'fetching-user-information';
  };

  setUser = ({
    displayName,
    permissions,
    canLogout,
    userId,
    salesPlanType,
    roles,
    c8Links,
    tenants,
  }: UserDto) => {
    storeStateLocally({
      wasReloaded: false,
    });

    this.state.status = 'user-information-fetched';
    this.state.displayName = displayName;
    this.state.canLogout = canLogout;
    this.state.userId = userId;
    this.state.salesPlanType = salesPlanType;
    this.state.roles = roles ?? [];
    this.state.permissions = permissions ?? DEFAULT_STATE.permissions;
    this.state.c8Links = c8Links;
    this.state.tenants = tenants;
  };

  handleLogout = async () => {
    const response = await logout();

    if (!response.isSuccess) {
      return new Error('Could not logout');
    }

    this.disableSession();
  };

  hasPermission = (scopes: Permissions) => {
    return this.state.permissions.some((permission) =>
      scopes.includes(permission),
    );
  };

  handleThirdPartySessionSuccess = () => {
    if (this.state.status === 'invalid-third-party-session') {
      this.authenticate();
    }
  };

  setStatus = (status: State['status']) => {
    this.state.status = status;
  };

  get tenantsById() {
    return this.state.tenants?.reduce<{[key: string]: string}>(
      (tenantsById, {tenantId, name}) => ({
        ...tenantsById,
        [tenantId]: name,
      }),
      {},
    );
  }
  resetUser = () => {
    this.state.displayName = DEFAULT_STATE.displayName;
    this.state.canLogout = DEFAULT_STATE.canLogout;
    this.state.permissions = DEFAULT_STATE.permissions;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const authenticationStore = new Authentication();
export type {Permissions};
