/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {Header} from '../index';
import {render, screen} from 'modules/testing-library';
import {processInstancesStore} from 'modules/stores/processInstances';
import {authenticationStore} from 'modules/stores/authentication';
import {createWrapper} from './mocks';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {createInstance, createUser} from 'modules/testUtils';
import {mockGetUser} from 'modules/mocks/api/getUser';

describe('license note', () => {
  beforeEach(() => {
    mockGetUser().withSuccess(createUser());
    mockFetchProcessInstance().withSuccess(
      createInstance({id: 'first_instance_id', state: 'ACTIVE'})
    );
    authenticationStore.authenticate();
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    authenticationStore.reset();
    processInstancesStore.reset();

    window.clientConfig = undefined;
  });

  it('should show license note in CCSM free/trial environment', async () => {
    window.clientConfig = {
      isEnterprise: false,
      organizationId: null,
    };

    render(<Header />, {
      wrapper: createWrapper(),
    });

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    expect(screen.getByText('Non-Production License')).toBeInTheDocument();
  });

  it('should not show license note in SaaS environment', async () => {
    window.clientConfig = {
      isEnterprise: false,
      organizationId: '000000000-0000-0000-0000-000000000000',
    };

    render(<Header />, {
      wrapper: createWrapper(),
    });

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    expect(
      screen.queryByText('Non-Production License')
    ).not.toBeInTheDocument();
  });

  it('should not show license note in CCSM enterprise environment', async () => {
    window.clientConfig = {
      isEnterprise: true,
      organizationId: null,
    };

    render(<Header />, {
      wrapper: createWrapper(),
    });

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    expect(
      screen.queryByText('Non-Production License')
    ).not.toBeInTheDocument();
  });
});
