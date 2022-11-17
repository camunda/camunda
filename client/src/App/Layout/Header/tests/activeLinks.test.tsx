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

describe('active links', () => {
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
  });

  it('should display processes as active when on processes page', async () => {
    render(<Header />, {
      wrapper: createWrapper('/processes'),
    });

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    expect(screen.getByTitle('View Processes')).toHaveAttribute(
      'aria-current',
      'page'
    );
  });

  it('should not display processes as active when on process detail page', async () => {
    render(<Header />, {
      wrapper: createWrapper('/processes/1'),
    });

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    expect(screen.getByTitle('View Processes')).not.toHaveAttribute(
      'aria-current',
      'page'
    );
  });

  it('should display decisions as active when on decisions page', async () => {
    render(<Header />, {
      wrapper: createWrapper('/decisions'),
    });

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    expect(screen.getByTitle('View Decisions')).toHaveAttribute(
      'aria-current',
      'page'
    );
  });

  it('should not display decisions as active when on decision detail page', async () => {
    render(<Header />, {
      wrapper: createWrapper('/decisions/1'),
    });

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    expect(screen.getByTitle('View Decisions')).not.toHaveAttribute(
      'aria-current',
      'page'
    );
  });

  it('should display dashboard as active when on dashboard page', async () => {
    render(<Header />, {
      wrapper: createWrapper('/'),
    });

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    const [logoNavLink, dashboardNavLink] =
      screen.getAllByTitle('View Dashboard');

    expect(logoNavLink).toHaveAttribute('aria-current', 'page');
    expect(dashboardNavLink).toHaveAttribute('aria-current', 'page');
  });
});
