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

import {render, screen} from '@testing-library/react';
import {MoveAction} from '..';
import {MemoryRouter} from 'react-router-dom';
import {useEffect} from 'react';
import {authenticationStore} from 'modules/stores/authentication';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {processesStore} from 'modules/stores/processes/processes.list';
import {groupedProcessesMock} from 'modules/testUtils';
import {Paths} from 'modules/Routes';

function getWrapper(initialPath: string = Paths.processes()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        authenticationStore.reset();
        processesStore.reset();
      };
    }, []);

    return (
      <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
    );
  };

  return Wrapper;
}

describe('<MoveAction /> - permissions', () => {
  it('should render move button when resource based permissions are not enabled', () => {
    render(<MoveAction />, {wrapper: getWrapper()});

    expect(screen.getByRole('button', {name: 'Move'})).toBeInTheDocument();
  });

  it('should not render, when the user does not have write permissions', () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
      tenants: [],
    });

    render(<MoveAction />, {wrapper: getWrapper()});

    expect(
      screen.queryByRole('button', {name: 'Move'}),
    ).not.toBeInTheDocument();
  });

  describe('resource based permissions', () => {
    beforeEach(() => {
      window.clientConfig = {
        resourcePermissionsEnabled: true,
      };
    });

    afterEach(() => {
      window.clientConfig = undefined;
    });

    it('should not render move button when resource based permissions are enabled without permission', async () => {
      mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
      await processesStore.fetchProcesses();

      render(<MoveAction />, {
        wrapper: getWrapper(
          '/processes?process=eventBasedGatewayProcess&version=1',
        ),
      });
      expect(
        screen.queryByRole('button', {name: 'Move'}),
      ).not.toBeInTheDocument();
    });

    it('should render move button when resource based permissions are enabled with permission', async () => {
      mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
      await processesStore.fetchProcesses();

      render(<MoveAction />, {
        wrapper: getWrapper('/processes?process=demoProcess&version=1'),
      });
      expect(screen.getByRole('button', {name: 'Move'})).toBeInTheDocument();
    });
  });
});
