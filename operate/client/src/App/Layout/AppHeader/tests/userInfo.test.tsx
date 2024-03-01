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

import {act, render, screen, waitFor} from 'modules/testing-library';
import {AppHeader} from '../index';
import {authenticationStore} from 'modules/stores/authentication';
import {mockLogout} from 'modules/mocks/api/logout';
import {mockGetUser} from 'modules/mocks/api/getUser';
import {createUser} from 'modules/testUtils';
import {Wrapper as BaseWrapper} from './mocks';
import {useEffect} from 'react';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return authenticationStore.reset;
  }, []);
  return <BaseWrapper>{children}</BaseWrapper>;
};

const mockUser = createUser({
  displayName: 'Franz Kafka',
  canLogout: true,
});

const mockSsoUser = createUser({
  displayName: 'Michael Jordan',
  canLogout: false,
});

describe('User info', () => {
  it('should render user display name', async () => {
    mockGetUser().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await act(async () => {
      await authenticationStore.authenticate();
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();
  });

  it('should handle a SSO user', async () => {
    mockGetUser().withSuccess(mockSsoUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await act(async () => {
      await authenticationStore.authenticate();
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Michael Jordan')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {
        name: /log out/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should handle logout', async () => {
    mockLogout().withSuccess(null);
    mockGetUser().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await act(async () => {
      await authenticationStore.authenticate();
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {
        name: /log out/i,
      }),
    );

    await user.click(
      screen.getByRole('button', {
        name: /settings/i,
      }),
    );

    await waitFor(() =>
      expect(screen.queryByText('Franz Kafka')).not.toBeInTheDocument(),
    );
  });

  it('should render links', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = jest.fn();
    window.open = mockOpenFn;

    mockGetUser().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await act(async () => {
      await authenticationStore.authenticate();
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Terms of use'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );
    await user.click(screen.getByRole('button', {name: 'Privacy policy'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/legal/privacy/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );
    await user.click(screen.getByRole('button', {name: 'Imprint'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/legal/imprint/',
      '_blank',
    );

    expect(
      screen.queryByRole('button', {name: 'Cookie preferences'}),
    ).not.toBeInTheDocument();

    window.open = originalWindowOpen;
  });

  it('should cookie preferences with correct link', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = jest.fn();
    const mockShowDrawer = jest.fn();

    window.open = mockOpenFn;
    window.Osano = {
      cm: {
        analytics: false,
        showDrawer: mockShowDrawer,
        addEventListener: jest.fn(),
      },
    };

    mockGetUser().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await act(async () => {
      await authenticationStore.authenticate();
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {name: 'Cookie preferences'}),
    );

    expect(mockShowDrawer).toHaveBeenLastCalledWith(
      'osano-cm-dom-info-dialog-open',
    );

    window.open = originalWindowOpen;
    window.Osano = undefined;
  });
});
