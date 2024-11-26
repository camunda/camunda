/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {Header} from '../index';
import {MemoryRouter} from 'react-router-dom';
import {createUser} from 'modules/testUtils';
import {authenticationStore} from 'modules/stores/authentication';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';

const MOCK_DECISION_INSTANCE_ID = '123567';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    authenticationStore.authenticate();

    return () => {
      authenticationStore.reset();
      decisionInstanceDetailsStore.reset();
    };
  }, []);

  return (
    <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
      {children}
    </MemoryRouter>
  );
};

describe('InstanceHeader', () => {
  afterEach(() => {
    window.clientConfig = undefined;
  });

  it('should render multi tenancy column and include tenant in version link', async () => {
    window.clientConfig = {
      multiTenancyEnabled: true,
    };

    mockFetchDecisionInstance().withSuccess(invoiceClassification);
    mockMe().withSuccess(
      createUser({
        tenants: [
          {tenantId: '<default>', name: 'Default Tenant'},
          {tenantId: 'tenant-a', name: 'Tenant A'},
        ],
      }),
    );

    decisionInstanceDetailsStore.fetchDecisionInstance(
      MOCK_DECISION_INSTANCE_ID,
    );

    render(<Header />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(await screen.findByText('Default Tenant')).toBeInTheDocument();

    expect(
      screen.getByRole('link', {
        name: 'View decision "Invoice Classification version 1" instances - Default Tenant',
      }),
    ).toHaveAttribute(
      'href',
      `${Paths.decisions()}?${new URLSearchParams({
        version: '1',
        name: 'invoiceClassification',
        evaluated: 'true',
        failed: 'true',
        tenant: '<default>',
      })}`,
    );
  });

  it('should hide multi tenancy column and exclude tenant from version link', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);
    mockMe().withSuccess(
      createUser({
        tenants: [
          {tenantId: '<default>', name: 'Default Tenant'},
          {tenantId: 'tenant-a', name: 'Tenant A'},
        ],
      }),
    );

    decisionInstanceDetailsStore.fetchDecisionInstance(
      MOCK_DECISION_INSTANCE_ID,
    );

    render(<Header />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByText('Default Tenant')).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        name: 'View decision "Invoice Classification version 1" instances',
      }),
    ).toBeInTheDocument();
  });
});
