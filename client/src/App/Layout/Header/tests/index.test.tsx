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
import {createInstance, createUser} from 'modules/testUtils';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockGetUser} from 'modules/mocks/api/getUser';
import {createWrapper} from './mocks';

describe('Header', () => {
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

  it('should render all header links', async () => {
    render(<Header />, {
      wrapper: createWrapper(),
    });

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    expect(screen.getByText('Operate')).toBeInTheDocument();
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Processes')).toBeInTheDocument();
    expect(screen.getByText('Decisions')).toBeInTheDocument();
  });

  it('should go to the correct pages when clicking on header links', async () => {
    const {user} = render(<Header />, {
      wrapper: createWrapper(),
    });

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    await user.click(await screen.findByText('Operate'));
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/);
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(await screen.findByText('Processes'));
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?active=true&incidents=true$/
    );

    await user.click(await screen.findByText('Dashboard'));
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/);
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(await screen.findByText('Decisions'));
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?evaluated=true&failed=true$/
    );
  });
});
